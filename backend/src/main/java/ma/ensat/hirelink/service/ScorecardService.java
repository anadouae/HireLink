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
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La grille de notation manuelle n'est plus utilisée. Le matching reste une aide à la présélection.");
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
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La grille de notation manuelle n'est plus utilisée. Le matching reste une aide à la présélection.");
    }

    @Transactional(readOnly = true)
    public List<ScorecardEntry> entries(User viewer, Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        organizationService.assertCanAccessApplication(viewer, app);
        return entryRepository.findByApplicationId(applicationId);
    }
}
