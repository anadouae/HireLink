package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline ATS — machine d'états R01, isolation org R02, checklist R05,
 * catalogue refus R04, retrait candidat R11, HM R14.
 * Parcours positif : … → INTERVIEW → OFFER → HIRED ; refus possible jusqu'à l'offre, retrait candidat tant que non terminal.
 */
@Service
public class ApplicationService {


    private static final Set<ApplicationStatus> TERMINAL = EnumSet.of(
            ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER, ApplicationStatus.HIRED,
            ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN
    );

    private static final Map<ApplicationStatus, String> STATUS_FR = Map.of(
            ApplicationStatus.SUBMITTED, "Envoyée",
            ApplicationStatus.SCREENING, "Présélection",
            ApplicationStatus.SHORTLISTED, "Shortlist",
            ApplicationStatus.INTERVIEW, "Entretien",
            ApplicationStatus.OFFER, "Offre",
            ApplicationStatus.HIRED, "Embauché(e)",
            ApplicationStatus.REJECTED, "Refusée",
            ApplicationStatus.WITHDRAWN, "Retirée"
    );

    private static final int MAX_APPLY_PER_DAY = 25;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
            ApplicationStatus.SUBMITTED, EnumSet.of(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED),
            ApplicationStatus.SCREENING, EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
            ApplicationStatus.SHORTLISTED, EnumSet.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
            ApplicationStatus.INTERVIEW, EnumSet.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED),
            ApplicationStatus.OFFER, EnumSet.of(ApplicationStatus.HIRED, ApplicationStatus.REJECTED)
    );

    private final ApplicationRepository applicationRepository;
    private final JobOfferRepository jobOfferRepository;
    private final SkillMatchingService matchingService;
    private final TimelineService timelineService;
    private final RefusalReasonRepository refusalReasonRepository;
    private final MatchingAnalysisRepository matchingAnalysisRepository;
    private final NotificationService notificationService;
    private final OrganizationService organizationService;
    private final MembershipRepository membershipRepository;
    private final CvStorage cvStorage;
    private final AuditService audit;
    private final InterviewService interviews;
    private final EventNotifier notifier;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            JobOfferRepository jobOfferRepository,
            SkillMatchingService matchingService,
            TimelineService timelineService,
            RefusalReasonRepository refusalReasonRepository,
            MatchingAnalysisRepository matchingAnalysisRepository,
            NotificationService notificationService,
            OrganizationService organizationService,
            MembershipRepository membershipRepository,
            CvStorage cvStorage,
            AuditService audit,
            InterviewService interviews,
            EventNotifier notifier
    ) {
        this.audit = audit;
        this.interviews = interviews;
        this.notifier = notifier;
        this.applicationRepository = applicationRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.matchingService = matchingService;
        this.timelineService = timelineService;
        this.refusalReasonRepository = refusalReasonRepository;
        this.matchingAnalysisRepository = matchingAnalysisRepository;
        this.notificationService = notificationService;
        this.organizationService = organizationService;
        this.membershipRepository = membershipRepository;
        this.cvStorage = cvStorage;
    }

    @Transactional
    public ApiDtos.ApplicationResponse apply(User candidate, ApiDtos.ApplyRequest req) {
        if (candidate.getRole() != Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
        Instant dayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        if (applicationRepository.countByCandidateIdAndSubmittedAtAfter(candidate.getId(), dayAgo) >= MAX_APPLY_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de candidatures atteinte (anti-abus)");
        }
        Long jobOfferId = req.jobOfferId();
        JobOffer offer = jobOfferRepository.findById(jobOfferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!offer.isPublished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offre non publiée");
        }
        if (offer.getDeadline() != null && offer.getDeadline().isBefore(java.time.LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date limite de candidature est dépassée");
        }
        var existing = applicationRepository.findByCandidateIdAndJobOfferId(candidate.getId(), jobOfferId);
        boolean reopen = existing.isPresent() && existing.get().getStatus() == ApplicationStatus.WITHDRAWN;
        if (existing.isPresent() && !reopen) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidature déjà existante (RM01)");
        }
        String cvSource = req.cvSource() == null || req.cvSource().isBlank() ? "PROFILE" : req.cvSource().trim().toUpperCase();
        if (!cvSource.equals("PROFILE") && !cvSource.equals("UPLOAD")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cvSource invalide");
        }
        if (cvSource.equals("UPLOAD") && (req.cvFileName() == null || req.cvFileName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV requis : choisissez un fichier");
        }
        Application app = reopen ? existing.get() : new Application();
        if (!reopen) {
            app.setCandidate(candidate);
            app.setJobOffer(offer);
        }
        app.setRejectionReason(null);
        app.setStatus(ApplicationStatus.SUBMITTED);
        Instant now = Instant.now();
        app.setSubmittedAt(now);
        app.setResponseDeadline(now.plus(offer.getResponseSlaDays(), ChronoUnit.DAYS));
        double score = matchingService.computeMatchScore(candidate.getSkills(), offer.getRequiredSkills());
        app.setMatchScore(score);
        app.setCvSource(cvSource);
        app.setCvFileName(req.cvFileName() != null && !req.cvFileName().isBlank()
                ? FileSignature.safeName(req.cvFileName())
                : (cvSource.equals("PROFILE") ? "Profil HireLink" : null));
        if (req.coverLetter() != null && !req.coverLetter().isBlank()) {
            String letter = req.coverLetter().trim();
            if (letter.length() > 8000) letter = letter.substring(0, 8000);
            app.setCoverLetter(letter);
        }
        String sections = cleanSections(req.cvSections());
        if (sections == null && "PROFILE".equals(cvSource)) {
            sections = candidate.getCvSections();
        }
        app.setCvSections(sections);
        if ("PROFILE".equals(cvSource)
                && candidate.getCvStorageName() != null
                && !candidate.getCvStorageName().isBlank()) {
            try {
                app.setCvStorageName(cvStorage.copy(candidate.getCvStorageName(), "applications"));
                if (candidate.getCvFileName() != null && !candidate.getCvFileName().isBlank()) {
                    app.setCvFileName(candidate.getCvFileName());
                }
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "CV du profil illisible");
            }
        }
        applicationRepository.save(app);

        MatchingAnalysis ma = new MatchingAnalysis();
        ma.setApplicationId(app.getId());
        ma.setScore(score);
        var detail = matchingService.explain(candidate.getSkills(), offer.getRequiredSkills());
        ma.setCoveredSkills(String.join(", ", detail.covered()));
        ma.setMissingSkills(String.join(", ", detail.missing()));
        matchingAnalysisRepository.save(ma);

        timelineService.append(app.getId(), reopen ? ApplicationStatus.WITHDRAWN.name() : null,
                ApplicationStatus.SUBMITTED.name(),
                candidate.getId(), "{\"event\":\"" + (reopen ? "REAPPLY" : "APPLY") + "\",\"match\":" + Math.round(score) + "}");
        notifier.applicationSubmitted(app);
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
        organizationService.assertSameOrg(recruiter, offer);
        List<Application> apps = applicationRepository.findByJobOfferId(offerId);
        return filterForActor(recruiter, apps).stream().map(this::toDto).toList();
    }

    /**
     * Pipeline org-wide (R02) ; Hiring Manager ne voit que ses dossiers assignés (R14).
     */
    @Transactional(readOnly = true)
    public List<ApiDtos.ApplicationResponse> pipelineForRecruiter(User recruiter) {
        if (recruiter.getRole() == Role.ADMINISTRATEUR) {
            return applicationRepository.findAllFetched().stream().map(this::toDto).toList();
        }
        Membership m = organizationService.requireMembership(recruiter);
        if (m.getOrgRole() == OrgRole.HIRING_MANAGER) {
            return applicationRepository.findByAssignedHmUserId(recruiter.getId()).stream()
                    .map(this::toDto).toList();
        }
        List<Long> memberIds = membershipRepository
                .findByOrganizationIdAndActiveTrue(m.getOrganization().getId())
                .stream()
                .map(x -> x.getUser().getId())
                .collect(Collectors.toList());
        if (memberIds.isEmpty()) {
            memberIds = List.of(recruiter.getId());
        }
        return applicationRepository.findByJobOfferRecruiterIdIn(memberIds).stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public ApiDtos.ApplicationResponse assignHm(User actor, Long appId, Long hmUserId) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        organizationService.assertSameOrg(actor, app.getJobOffer());
        Membership actorM = organizationService.requireMembership(actor);
        if (actorM.getOrgRole() == OrgRole.HIRING_MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul Owner/Recruteur assigne un HM (R14)");
        }
        if (hmUserId != null) {
            Membership hm = membershipRepository
                    .findByOrganizationIdAndUserId(actorM.getOrganization().getId(), hmUserId)
                    .filter(Membership::isActive)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "HM hors organisation (R02)"));
            if (hm.getOrgRole() != OrgRole.HIRING_MANAGER && hm.getOrgRole() != OrgRole.RECRUTEUR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle cible invalide pour assignation");
            }
        }
        app.setAssignedHmUserId(hmUserId);
        applicationRepository.save(app);
        audit.ok("HM_ASSIGNED", actor, "APPLICATION", app.getId(), "HM " + hmUserId);
        if (hmUserId != null) {
            notificationService.push(hmUserId, "Dossier assigné",
                    "Vous êtes Hiring Manager sur « " + app.getJobOffer().getTitle() + " »", "ASSIGN");
        }
        return toDto(app);
    }

    @Transactional
    public ApiDtos.ApplicationResponse advance(User actor, Long appId, ApiDtos.AdvanceRequest req) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));

        ApplicationStatus from = app.getStatus();

        if (req.status() == ApplicationStatus.WITHDRAWN) {
            if (!app.getCandidate().getId().equals(actor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le candidat peut retirer (R11)");
            }
            // Retrait possible jusqu'à l'embauche (retirer une candidature en « Offre » revient à décliner l'offre).
            if (from == ApplicationStatus.REJECTED || from == ApplicationStatus.WITHDRAWN || from == ApplicationStatus.HIRED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Retrait impossible à cette étape (R11)");
            }
            app.setStatus(ApplicationStatus.WITHDRAWN);
            applicationRepository.save(app);
            audit.ok("STAGE_CHANGE", actor, "APPLICATION", app.getId(), from + " → WITHDRAWN");
            timelineService.append(app.getId(), from.name(), ApplicationStatus.WITHDRAWN.name(),
                    actor.getId(), "{\"event\":\"WITHDRAW\"}");
            for (Long target : organizationService.offerNotificationTargets(app.getJobOffer())) {
                notificationService.push(target,
                        "Candidature retirée",
                        app.getCandidate().getFullName() + " a retiré sa candidature",
                        "STATUS");
            }
            return toDto(app);
        }

        assertCanActOnPipeline(actor, app);

        Set<ApplicationStatus> allowed = ALLOWED.getOrDefault(app.getStatus(), EnumSet.noneOf(ApplicationStatus.class));
        if (!allowed.contains(req.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette étape ne peut pas suivre " + app.getStatus() + ".");
        }

        if (!Boolean.TRUE.equals(req.confirmIdentity())
                || !Boolean.TRUE.equals(req.confirmCvReviewed())
                || !Boolean.TRUE.equals(req.confirmSlaAware())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmez l’identité, la lecture du CV et le délai avant de continuer.");
        }

        if (req.status() == ApplicationStatus.REJECTED) {
            applyRefusalCatalog(app, req.rejectionReason());
        }

        if (req.status() == ApplicationStatus.INTERVIEW) {
            if (req.interview() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Planifiez l'entretien : type, date et heure sont requis.");
            }
            interviews.schedule(app, req.interview());
        }

        if (req.status() == ApplicationStatus.REJECTED || req.status() == ApplicationStatus.INTERVIEW) {
            if (app.getFirstDecisionAt() == null && req.status() == ApplicationStatus.REJECTED) {
                app.setFirstDecisionAt(Instant.now());
            }
        }

        if (req.advanceNote() != null && !req.advanceNote().isBlank()) {
            String note = req.advanceNote().trim();
            if (note.length() > 1000) note = note.substring(0, 1000);
            app.setAdvanceNote(note);
        }

        app.setStatus(req.status());
        applicationRepository.save(app);

        audit.ok("STAGE_CHANGE", actor, "APPLICATION", app.getId(), from + " → " + req.status());
        String payload = buildTimelinePayload(app, req);
        timelineService.append(app.getId(), from.name(), req.status().name(), actor.getId(), payload);
        if (req.status() == ApplicationStatus.INTERVIEW) {
            notifier.interviewScheduled(app, false);
        } else {
            notifier.statusChanged(app);
        }
        if (app.getAssignedHmUserId() != null && !app.getAssignedHmUserId().equals(actor.getId())) {
            notificationService.push(app.getAssignedHmUserId(),
                    "Pipeline mis à jour",
                    app.getCandidate().getFullName() + " → " + STATUS_FR.get(req.status()),
                    "STATUS");
        }
        return toDto(app);
    }

    private void assertCanActOnPipeline(User actor, Application app) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return;
        boolean hmAssigned = app.getAssignedHmUserId() != null
                && app.getAssignedHmUserId().equals(actor.getId());
        if (hmAssigned) {
            organizationService.assertSameOrg(actor, app.getJobOffer());
            return;
        }
        organizationService.assertSameOrg(actor, app.getJobOffer());
        Membership m = organizationService.requireMembership(actor);
        if (m.getOrgRole() == OrgRole.HIRING_MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "HM : dossier non assigné (R14)");
        }
    }

    private List<Application> filterForActor(User actor, List<Application> apps) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return apps;
        try {
            Membership m = organizationService.requireMembership(actor);
            if (m.getOrgRole() == OrgRole.HIRING_MANAGER) {
                return apps.stream()
                        .filter(a -> actor.getId().equals(a.getAssignedHmUserId()))
                        .toList();
            }
        } catch (ResponseStatusException ignored) {
            // pas d'org → fallback ownership offre
        }
        return apps;
    }

    private void applyRefusalCatalog(Application app, String reasonRaw) {
        if (reasonRaw == null || reasonRaw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un motif de refus.");
        }
        String reason = reasonRaw.trim();
        if (reason.regionMatches(true, 0, "OTHER:", 0, 6)) {
            String note = reason.substring(6).trim();
            if (note.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Précisez le motif.");
            }
            if (note.length() > 500) note = note.substring(0, 500);
            app.setRefusalReasonCode("OTHER_CATALOG");
            app.setRejectionReason(note);
            return;
        }
        RefusalReason catalog = refusalReasonRepository.findByCode(reason)
                .or(() -> refusalReasonRepository.findByActiveTrue().stream()
                        .filter(rr -> rr.getLabel().equalsIgnoreCase(reason))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Choisissez un motif de la liste."));
        app.setRefusalReasonCode(catalog.getCode());
        app.setRejectionReason(catalog.getLabel());
    }

    /** Seul le refus porte une information utile en plus du changement d'étape : son motif. */
    private static String buildTimelinePayload(Application app, ApiDtos.AdvanceRequest req) {
        if (req.status() != ApplicationStatus.REJECTED || app.getRejectionReason() == null) return null;
        return "{\"reason\":\"" + jsonEsc(app.getRejectionReason()) + "\"}";
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ").replace("\r", "");
    }

    private ApiDtos.ApplicationResponse toDto(Application app) {
        JobOffer o = app.getJobOffer();
        Instant deadline = app.getResponseDeadline();
        if (deadline == null && app.getSubmittedAt() != null) {
            deadline = app.getSubmittedAt().plus(o.getResponseSlaDays(), ChronoUnit.DAYS);
        }
        Long remaining = null; // le délai de réponse ne s’applique plus une fois entretien, refus ou retrait
        boolean breached = false;
        if (deadline != null && !TERMINAL.contains(app.getStatus())) {
            remaining = ChronoUnit.DAYS.between(Instant.now(), deadline);
            breached = Instant.now().isAfter(deadline);
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
                app.getRefusalReasonCode(),
                app.getCvSource(),
                app.getCvFileName(),
                app.getCoverLetter(),
                app.getInterviewType(),
                app.getInterviewAt(),
                app.getInterviewLocation(),
                app.getInterviewNotes(),
                app.getInterviewKind(),
                app.getInterviewDurationMinutes(),
                app.getInterviewTimeZone(),
                app.getInterviewVideoUrl(),
                app.getInterviewPhone(),
                app.getInterviewMapUrl(),
                app.getInterviewParticipants(),
                app.getInterviewAt() == null ? null : app.getInterviewStatus() == null ? InterviewService.SCHEDULED : app.getInterviewStatus(),
                app.getInterviewCancelReason(),
                app.getInterviewAt() == null ? null : app.getInterviewConfirmation() == null ? InterviewService.PENDING : app.getInterviewConfirmation(),
                app.getInterviewCandidateNote(),
                app.getAdvanceNote(),
                app.getAssignedHmUserId(),
                app.getSubmittedAt(),
                app.getUpdatedAt(),
                deadline,
                o.getResponseSlaDays(),
                remaining,
                breached,
                app.getCvSections(),
                app.getCvStorageName() != null && !app.getCvStorageName().isBlank()
        );
    }

    /** Reporter un entretien (ou en replanifier un annulé) : équipe de l'organisation uniquement. */
    @Transactional
    public ApiDtos.ApplicationResponse rescheduleInterview(User actor, Long appId, ApiDtos.InterviewRequest req) {
        Application app = interviewApplication(actor, appId);
        interviews.schedule(app, req);
        applicationRepository.save(app);
        audit.ok("INTERVIEW_RESCHEDULED", actor, "APPLICATION", app.getId(), interviews.summary(app));
        notifier.interviewScheduled(app, true);
        return toDto(app);
    }

    @Transactional
    public ApiDtos.ApplicationResponse cancelInterview(User actor, Long appId, String reason) {
        Application app = interviewApplication(actor, appId);
        interviews.cancel(app, reason);
        applicationRepository.save(app);
        audit.ok("INTERVIEW_CANCELLED", actor, "APPLICATION", app.getId(), reason);
        notifier.interviewCancelled(app);
        return toDto(app);
    }

    /** Le candidat confirme sa présence ou signale qu'il n'est pas disponible. */
    @Transactional
    public ApiDtos.ApplicationResponse answerInterview(User candidate, Long appId, ApiDtos.InterviewAnswerRequest req) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        if (!candidate.getId().equals(app.getCandidate().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce dossier ne vous appartient pas");
        }
        interviews.answer(app, req.available(), req.note());
        applicationRepository.save(app);
        notifier.interviewAnswered(app);
        return toDto(app);
    }

    public record CalendarFile(byte[] content, String filename) {}

    /** Fichier .ics de l'entretien : le candidat concerné ou l'équipe de l'organisation. */
    @Transactional(readOnly = true)
    public CalendarFile interviewCalendar(User actor, Long appId) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        if (!actor.getId().equals(app.getCandidate().getId())) {
            organizationService.assertCanAccessApplication(actor, app);
        }
        if (app.getInterviewAt() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun entretien planifié");
        }
        return new CalendarFile(interviews.ics(app), "entretien-hirelink-" + app.getId() + ".ics");
    }

    private Application interviewApplication(User actor, Long appId) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        assertCanActOnPipeline(actor, app);
        if (app.getStatus() != ApplicationStatus.INTERVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce dossier n'est pas à l'étape entretien.");
        }
        return app;
    }

    public record CvFile(Resource resource, String filename, String mediaType) {}

    @Transactional
    public ApiDtos.ApplicationResponse attachCv(User candidate, Long appId, MultipartFile file, String sections) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        if (!candidate.getId().equals(app.getCandidate().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce dossier ne vous appartient pas");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un CV");
        }
        try {
            app.setCvStorageName(cvStorage.save("applications", file.getOriginalFilename(), file.getBytes()));
            app.setCvFileName(FileSignature.safeName(file.getOriginalFilename()));
            app.setCvSource("UPLOAD");
            String clean = cleanSections(sections);
            if (clean != null) app.setCvSections(clean);
            applicationRepository.save(app);
            return toDto(app);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Enregistrement du CV impossible");
        }
    }

    @Transactional(readOnly = true)
    public CvFile loadApplicationCv(User actor, Long appId) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        if (!actor.getId().equals(app.getCandidate().getId())) {
            organizationService.assertCanAccessApplication(actor, app);
        }
        if (app.getCvStorageName() == null || app.getCvStorageName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun CV joint");
        }
        Resource resource = cvStorage.load(app.getCvStorageName());
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier CV introuvable");
        }
        String name = app.getCvFileName() == null || app.getCvFileName().isBlank() ? "cv" : app.getCvFileName();
        return new CvFile(resource, name, cvStorage.mediaType(app.getCvStorageName()));
    }

    private String cleanSections(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) return null;
        return trimmed.length() > 8000 ? trimmed.substring(0, 8000) : trimmed;
    }
}
