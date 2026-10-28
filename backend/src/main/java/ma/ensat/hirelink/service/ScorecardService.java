package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ScorecardService {
    private final ScorecardCriterionRepository criterionRepository;
    private final ScorecardEntryRepository entryRepository;
    private final JobOfferRepository jobOfferRepository;
    private final ApplicationRepository applicationRepository;
    private final OrganizationService organizationService;

    public ScorecardService(ScorecardCriterionRepository criterionRepository,
                            ScorecardEntryRepository entryRepository,
                            JobOfferRepository jobOfferRepository,
                            ApplicationRepository applicationRepository,
                            OrganizationService organizationService) {
        this.organizationService = organizationService;
        this.criterionRepository = criterionRepository;
        this.entryRepository = entryRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public List<ScorecardCriterion> setCriteria(User recruiter, Long offerId, List<Map<String, Object>> items) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!organizationService.canManageOffer(recruiter, offer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pas propriétaire");
        }
        criterionRepository.findByJobOfferId(offerId).forEach(criterionRepository::delete);
        List<ScorecardCriterion> saved = new ArrayList<>();
        for (Map<String, Object> it : items) {
            ScorecardCriterion c = new ScorecardCriterion();
            c.setJobOfferId(offerId);
            String label = String.valueOf(it.getOrDefault("label", "Critère")).strip();
            c.setLabel(label.isEmpty() ? "Critère" : label.substring(0, Math.min(label.length(), 120)));
            c.setWeight(Math.max(1, ((Number) it.getOrDefault("weight", 1)).intValue()));
            c.setVeto(Boolean.TRUE.equals(it.get("veto")));
            saved.add(criterionRepository.save(c));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ScorecardCriterion> criteria(User viewer, Long offerId) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        organizationService.assertSameOrg(viewer, offer);
        return criterionRepository.findByJobOfferId(offerId);
    }

    @Transactional
    public ScorecardEntry rate(User evaluator, Long applicationId, Long criterionId, int score, String comment) {
        if (score < 1 || score > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note entre 1 et 5");
        }
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        ScorecardCriterion c = criterionRepository.findById(criterionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Critère introuvable"));
        if (!c.getJobOfferId().equals(app.getJobOffer().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Critère hors offre");
        }
        // R02 / R14 : membres de l'organisation de l'offre ; un Hiring Manager seulement sur ses dossiers assignés
        organizationService.assertCanAccessApplication(evaluator, app);
        // Une note par (dossier, critère, évaluateur) : on met à jour au lieu d'empiler des doublons
        ScorecardEntry e = entryRepository
                .findFirstByApplicationIdAndCriterionIdAndEvaluatorUserId(applicationId, criterionId, evaluator.getId())
                .orElseGet(ScorecardEntry::new);
        e.setApplicationId(applicationId);
        e.setCriterionId(criterionId);
        e.setEvaluatorUserId(evaluator.getId());
        e.setScore(score);
        e.setComment(comment == null ? null : comment.substring(0, Math.min(comment.length(), 1000)));
        return entryRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<ScorecardEntry> entries(User viewer, Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        organizationService.assertCanAccessApplication(viewer, app);
        return entryRepository.findByApplicationId(applicationId);
    }
}
