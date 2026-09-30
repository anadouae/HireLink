package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApplicationService {

    private static final Set<ApplicationStatus> TERMINAL = EnumSet.of(
            ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN
    );

    private final ApplicationRepository applicationRepository;
    private final JobOfferRepository jobOfferRepository;
    private final SkillMatchingService matchingService;
    private final TimelineService timelineService;
    private final RefusalReasonRepository refusalReasonRepository;
    private final MatchingAnalysisRepository matchingAnalysisRepository;
    private final NotificationService notificationService;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            JobOfferRepository jobOfferRepository,
            SkillMatchingService matchingService,
            TimelineService timelineService,
            RefusalReasonRepository refusalReasonRepository,
            MatchingAnalysisRepository matchingAnalysisRepository,
            NotificationService notificationService
    ) {
        this.applicationRepository = applicationRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.matchingService = matchingService;
        this.timelineService = timelineService;
        this.refusalReasonRepository = refusalReasonRepository;
        this.matchingAnalysisRepository = matchingAnalysisRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ApiDtos.ApplicationResponse apply(User candidate, ApiDtos.ApplyRequest req) {
        if (candidate.getRole() != Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
        Long jobOfferId = req.jobOfferId();
        JobOffer offer = jobOfferRepository.findById(jobOfferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!offer.isPublished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offre non publiée");
        }
        if (applicationRepository.findByCandidateIdAndJobOfferId(candidate.getId(), jobOfferId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidature déjà existante (RM01)");
        }
        String cvSource = req.cvSource() == null || req.cvSource().isBlank() ? "PROFILE" : req.cvSource().trim().toUpperCase();
        if (!cvSource.equals("PROFILE") && !cvSource.equals("UPLOAD")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cvSource invalide");
        }
        if (cvSource.equals("UPLOAD") && (req.cvFileName() == null || req.cvFileName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV requis : choisissez un fichier");
        }
        Application app = new Application();
        app.setCandidate(candidate);
        app.setJobOffer(offer);
        app.setStatus(ApplicationStatus.SUBMITTED);
        double score = matchingService.computeMatchScore(candidate.getSkills(), offer.getRequiredSkills());
        app.setMatchScore(score);
        app.setCvSource(cvSource);
        app.setCvFileName(req.cvFileName() != null && !req.cvFileName().isBlank()
                ? req.cvFileName().trim()
                : (cvSource.equals("PROFILE") ? "Profil HireLink" : null));
        if (req.coverLetter() != null && !req.coverLetter().isBlank()) {
            String letter = req.coverLetter().trim();
            if (letter.length() > 8000) letter = letter.substring(0, 8000);
            app.setCoverLetter(letter);
        }
        if (req.photoFileName() != null && !req.photoFileName().isBlank()) {
            app.setPhotoFileName(req.photoFileName().trim());
        }
        applicationRepository.save(app);

        MatchingAnalysis ma = new MatchingAnalysis();
        ma.setApplicationId(app.getId());
        ma.setScore(score);
        var detail = matchingService.explain(candidate.getSkills(), offer.getRequiredSkills());
        ma.setCoveredSkills(String.join(", ", detail.covered()));
        ma.setMissingSkills(String.join(", ", detail.missing()));
        matchingAnalysisRepository.save(ma);

        timelineService.append(app.getId(), null, ApplicationStatus.SUBMITTED.name(),
                candidate.getId(), "{\"event\":\"APPLY\"}");
        notificationService.push(offer.getRecruiter().getId(),
                "Nouvelle candidature",
                candidate.getFullName() + " a postulé à « " + offer.getTitle() + " »",
                "APPLICATION");
        notificationService.push(candidate.getId(),
                "Candidature envoyée",
                "Votre dossier pour « " + offer.getTitle() + " » est soumis. SLA : "
                        + offer.getResponseSlaDays() + " j.",
                "APPLICATION");
        return toDto(app);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ApplicationResponse> mineAsCandidate(User candidate) {
        return applicationRepository.findByCandidateId(candidate.getId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ApplicationResponse> forOffer(User recruiter, Long offerId) {
        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        assertOwns(recruiter, offer);
        return applicationRepository.findByJobOfferId(offerId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ApplicationResponse> pipelineForRecruiter(User recruiter) {
        return applicationRepository.findByJobOfferRecruiterId(recruiter.getId()).stream().map(this::toDto).toList();
    }

    /** R14 — assigner un Hiring Manager à un dossier. */
    @Transactional
    public ApiDtos.ApplicationResponse assignHm(User actor, Long appId, Long hmUserId) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        assertOwns(actor, app.getJobOffer());
        app.setAssignedHmUserId(hmUserId);
        applicationRepository.save(app);
        timelineService.append(app.getId(), app.getStatus().name(), app.getStatus().name(),
                actor.getId(), "{\"event\":\"ASSIGN_HM\",\"hmUserId\":" + hmUserId + "}");
        notificationService.push(hmUserId, "Dossier assigné",
                "Vous êtes Hiring Manager sur « " + app.getJobOffer().getTitle() + " »", "ASSIGN");
        return toDto(app);
    }

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
            ApplicationStatus.SUBMITTED, EnumSet.of(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED),
            ApplicationStatus.SCREENING, EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
            ApplicationStatus.SHORTLISTED, EnumSet.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
            ApplicationStatus.INTERVIEW, EnumSet.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED),
            ApplicationStatus.OFFER, EnumSet.of(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED)
    );

    @Transactional
    public ApiDtos.ApplicationResponse advance(User actor, Long appId, ApiDtos.AdvanceRequest req) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));

        ApplicationStatus from = app.getStatus();

        if (req.status() == ApplicationStatus.WITHDRAWN) {
            if (!app.getCandidate().getId().equals(actor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le candidat peut retirer (R11)");
            }
            app.setStatus(ApplicationStatus.WITHDRAWN);
            applicationRepository.save(app);
            timelineService.append(app.getId(), from.name(), ApplicationStatus.WITHDRAWN.name(),
                    actor.getId(), "{\"event\":\"WITHDRAW\"}");
            return toDto(app);
        }

        // R14 : HM assigné peut agir sur son dossier ; sinon ownership offre
        boolean hmAssigned = actor.getRole() == Role.RECRUTEUR
                && app.getAssignedHmUserId() != null
                && app.getAssignedHmUserId().equals(actor.getId());
        if (!hmAssigned) {
            assertOwns(actor, app.getJobOffer());
        }

        Set<ApplicationStatus> allowed = ALLOWED.getOrDefault(app.getStatus(), EnumSet.noneOf(ApplicationStatus.class));
        if (!allowed.contains(req.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transition interdite (R01) : " + app.getStatus() + " → " + req.status());
        }

        if (!Boolean.TRUE.equals(req.confirmIdentity())
                || !Boolean.TRUE.equals(req.confirmCvReviewed())
                || !Boolean.TRUE.equals(req.confirmSlaAware())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Checklist obligatoire incomplète (R05) : identité, CV lu, SLA");
        }

        if (req.status() == ApplicationStatus.REJECTED) {
            if (req.rejectionReason() == null || req.rejectionReason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motif de refus obligatoire (R04)");
            }
            String reason = req.rejectionReason().trim();
            RefusalReason catalog = refusalReasonRepository.findByCode(reason)
                    .or(() -> refusalReasonRepository.findByActiveTrue().stream()
                            .filter(rr -> rr.getLabel().equalsIgnoreCase(reason))
                            .findFirst())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Motif hors catalogue (R04) — choisissez un code catalogue"));
            app.setRefusalReasonCode(catalog.getCode());
            app.setRejectionReason(catalog.getLabel());
        }

        if (req.status() == ApplicationStatus.INTERVIEW) {
            if (req.interviewType() == null || req.interviewType().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type d’entretien requis");
            }
            String type = req.interviewType().trim().toUpperCase();
            if (!Set.of("PHONE", "VISIO", "ONSITE", "ASYNC").contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type d’entretien invalide");
            }
            if (req.interviewAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date/heure d’entretien requise");
            }
            app.setInterviewType(type);
            app.setInterviewAt(req.interviewAt());
            if (req.interviewLocation() != null && !req.interviewLocation().isBlank()) {
                app.setInterviewLocation(req.interviewLocation().trim());
            }
            if (req.interviewNotes() != null && !req.interviewNotes().isBlank()) {
                app.setInterviewNotes(req.interviewNotes().trim());
            }
        }

        if (req.advanceNote() != null && !req.advanceNote().isBlank()) {
            app.setAdvanceNote(req.advanceNote().trim());
        }

        app.setStatus(req.status());
        applicationRepository.save(app);
        String payload = "{\"rejectionReason\":\""
                + (app.getRejectionReason() == null ? "" : app.getRejectionReason().replace("\"", "'"))
                + "\",\"refusalCode\":\""
                + (app.getRefusalReasonCode() == null ? "" : app.getRefusalReasonCode())
                + "\"}";
        timelineService.append(app.getId(), from.name(), req.status().name(), actor.getId(), payload);
        notificationService.push(app.getCandidate().getId(),
                "Mise à jour candidature",
                "« " + app.getJobOffer().getTitle() + " » → " + req.status().name()
                        + (req.status() == ApplicationStatus.REJECTED && app.getRejectionReason() != null
                        ? " — " + app.getRejectionReason() : ""),
                "STATUS");
        return toDto(app);
    }

    private void assertOwns(User recruiter, JobOffer offer) {
        if (recruiter.getRole() == Role.ADMINISTRATEUR) return;
        if (recruiter.getRole() != Role.RECRUTEUR
                || !offer.getRecruiter().getId().equals(recruiter.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé (R02)");
        }
    }

    private ApiDtos.ApplicationResponse toDto(Application app) {
        JobOffer o = app.getJobOffer();
        long remaining = ChronoUnit.DAYS.between(Instant.now(),
                app.getSubmittedAt().plus(o.getResponseSlaDays(), ChronoUnit.DAYS));
        if (TERMINAL.contains(app.getStatus())) {
            remaining = 0;
        }
        return new ApiDtos.ApplicationResponse(
                app.getId(),
                o.getId(),
                o.getTitle(),
                app.getCandidate().getId(),
                app.getCandidate().getFullName(),
                app.getCandidate().getEmail(),
                app.getCandidate().getSkills(),
                app.getStatus(),
                app.getMatchScore(),
                app.getRejectionReason(),
                app.getCvSource(),
                app.getCvFileName(),
                app.getCoverLetter(),
                app.getPhotoFileName(),
                app.getInterviewType(),
                app.getInterviewAt(),
                app.getInterviewLocation(),
                app.getInterviewNotes(),
                app.getAdvanceNote(),
                app.getSubmittedAt(),
                app.getUpdatedAt(),
                o.getResponseSlaDays(),
                remaining
        );
    }
}
