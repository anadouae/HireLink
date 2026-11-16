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
 * Parcours positif : … → INTERVIEW (fin HireLink) ; refus possible jusqu'à l'entretien, retrait candidat tant que non terminal.
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
            ApplicationStatus.INTERVIEW, EnumSet.of(ApplicationStatus.REJECTED)
    );

    private final ApplicationRepository applicationRepository;
    private final JobOfferRepository jobOfferRepository;
    private final SkillMatchingService matchingService;
    private final TimelineService timelineService;
    private final RefusalReasonRepository refusalReasonRepository;
    private final NotificationService notificationService;
    private final OrganizationService organizationService;
    private final MembershipRepository membershipRepository;
    private final CvStorage cvStorage;
    private final AuditService audit;
    private final InterviewService interviews;
    private final EventNotifier notifier;
    private final ResponsivenessService responsiveness;
    private final MailService mail;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            JobOfferRepository jobOfferRepository,
            SkillMatchingService matchingService,
            TimelineService timelineService,
            RefusalReasonRepository refusalReasonRepository,
            NotificationService notificationService,
            OrganizationService organizationService,
            MembershipRepository membershipRepository,
            CvStorage cvStorage,
            AuditService audit,
            InterviewService interviews,
            EventNotifier notifier,
            ResponsivenessService responsiveness,
            MailService mail
    ) {
        this.audit = audit;
        this.interviews = interviews;
        this.notifier = notifier;
        this.responsiveness = responsiveness;
        this.mail = mail;
        this.applicationRepository = applicationRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.matchingService = matchingService;
        this.timelineService = timelineService;
        this.refusalReasonRepository = refusalReasonRepository;
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
        Long jobOfferId = req == null ? null : req.jobOfferId();
        // Les annonces Web éphémères exposent un id texte (web-…) : refus explicite, jamais un 500 JPA.
        if (jobOfferId == null || jobOfferId <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "jobOfferId manquant ou invalide — seules les offres HireLink numériques acceptent une candidature interne");
        }
        JobOffer offer = jobOfferRepository.findById(jobOfferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!offer.isPublished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offre non publiée");
        }
        if (offer.getClosedAt() != null || offer.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    offer.getArchivedAt() != null ? "Cette offre n'est plus ouverte" : "Les candidatures de cette offre sont closes");
        }
        if (offer.getDeadline() != null && offer.getDeadline().isBefore(BusinessTime.today())) {
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
        if (cvSource.equals("PROFILE")
                && (candidate.getCvStorageName() == null || candidate.getCvStorageName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Profil HireLink sans CV : déposez un CV dans Mon profil, ou choisissez « Nouveau CV ».");
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
        var compat = matchingService.evaluateIfAvailable(candidate, offer);
        Double score = compat == null ? null : compat.score();
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
        app.setApplicationDetails(cleanDetails(req.details()));
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

        timelineService.append(app.getId(), reopen ? ApplicationStatus.WITHDRAWN.name() : null,
                ApplicationStatus.SUBMITTED.name(),
                candidate.getId(), "{\"event\":\"" + (reopen ? "REAPPLY" : "APPLY") + "\""
                        + (score == null ? "" : ",\"match\":" + Math.round(score)) + "}");
        notifier.applicationSubmitted(app);
        return toDto(app);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ApplicationResponse> mineAsCandidate(User candidate) {
        return applicationRepository.findByCandidateId(candidate.getId()).stream()
                .map(a -> toDto(a, true))
                .toList();
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
     * Retour de vélocité du tableau de bord : les délais déjà constatés sur les offres du recruteur
     * connecté. Un Hiring Manager, qui ne possède pas d'offres, reçoit un signal vide.
     */
    @Transactional(readOnly = true)
    public ApiDtos.PaceSignal paceForRecruiter(User recruiter) {
        ResponsivenessService.Signal s = responsiveness.signalOfRecruiter(recruiter.getId());
        return new ApiDtos.PaceSignal(s.answered(), s.medianDays(), s.badge());
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
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "L'affectation à un responsable du poste n'est plus proposée. Les affectations déjà enregistrées sont conservées.");
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
                notificationService.push("application:" + app.getId(), target,
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

        // Première décision recruteur (avancement ou refus) — pas seulement REJECTED (SLA 1ʳᵉ réponse).
        if (app.getFirstDecisionAt() == null
                && req.status() != ApplicationStatus.WITHDRAWN
                && req.status() != ApplicationStatus.SUBMITTED) {
            app.setFirstDecisionAt(Instant.now());
        }

        if (req.advanceNote() != null && !req.advanceNote().isBlank()) {
            String note = req.advanceNote().trim();
            if (note.length() > 4000) note = note.substring(0, 4000);
            app.setAdvanceNote(note);
        }

        // Refus : annuler proprement un entretien encore actif (historique conservé, plus de convocation active).
        boolean interviewCancelledOnReject = false;
        if (req.status() == ApplicationStatus.REJECTED && interviews.isActive(app)) {
            interviews.cancel(app, "Candidature refusée — entretien annulé automatiquement.");
            interviewCancelledOnReject = true;
        }

        app.setStatus(req.status());
        applicationRepository.save(app);

        audit.ok("STAGE_CHANGE", actor, "APPLICATION", app.getId(), from + " → " + req.status());
        if (req.status() == ApplicationStatus.REJECTED) {
            audit.ok("APPLICATION_REFUSED", actor, "APPLICATION", app.getId(), app.getRefusalReasonCode());
            if (interviewCancelledOnReject) {
                audit.ok("INTERVIEW_CANCELLED", actor, "APPLICATION", app.getId(), "auto-on-reject");
            }
        }
        String payload = buildTimelinePayload(app, req);
        timelineService.append(app.getId(), from.name(), req.status().name(), actor.getId(), payload);
        // Invitation e-mail uniquement si un créneau existe déjà : sinon, on annonce l'étape et rien de plus.
        if (req.status() == ApplicationStatus.INTERVIEW && interviews.isPlanned(app)) {
            notifier.interviewScheduled(app, false);
        } else if (interviewCancelledOnReject) {
            notifier.interviewCancelled(app);
            notifier.statusChanged(app);
        } else {
            notifier.statusChanged(app);
        }
        if (app.getAssignedHmUserId() != null && !app.getAssignedHmUserId().equals(actor.getId())) {
            notificationService.push("application:" + app.getId(), app.getAssignedHmUserId(),
                    "Pipeline mis à jour",
                    app.getCandidate().getFullName() + " → " + STATUS_FR.get(req.status()),
                    "STATUS");
        }
        return toDto(app);
    }

    private void assertCanActOnPipeline(User actor, Application app) {
        if (actor.getRole() == Role.ADMINISTRATEUR) return;
        // A5 : rôle vérifié explicitement, pas par l'effet de bord d'une absence d'organisation.
        if (actor.getRole() != Role.RECRUTEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Pipeline réservé aux comptes recruteurs (R11)");
        }
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
        int split = reason.indexOf(':');
        if ("REF06".equalsIgnoreCase(reason) || "OTHER".equalsIgnoreCase(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Précisez le motif libre pour Autre.");
        }
        if (split > 0 && (reason.regionMatches(true, 0, "OTHER:", 0, 6) || reason.regionMatches(true, 0, "REF06:", 0, 6))) {
            String note = reason.substring(split + 1).trim();
            if (note.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Précisez le motif libre pour Autre.");
            }
            if (note.length() > 500) note = note.substring(0, 500);
            String code = reason.regionMatches(true, 0, "REF06:", 0, 6) ? "REF06" : "OTHER_CATALOG";
            app.setRefusalReasonCode(code);
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
        return toDto(app, false);
    }

    /** @param candidateView masque les notes internes confidentielles. */
    private ApiDtos.ApplicationResponse toDto(Application app, boolean candidateView) {
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
        var compat = matchingService.evaluateIfAvailable(app.getCandidate(), o);
        Double currentScore = compat == null ? null : compat.score();
        ResponsivenessService.Signal pace = responsiveness.signalOf(o.getRecruiter().getAssignedOrgId());
        return new ApiDtos.ApplicationResponse(
                app.getId(),
                o.getId(),
                o.getTitle(),
                app.getCandidate().getId(),
                app.getCandidate().getFullName(),
                app.getCandidate().getEmail(),
                app.getCandidate().getSkills(),
                app.getStatus(),
                currentScore,
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
                app.getInterviewMapUrl(),
                app.getInterviewParticipants(),
                interviewStatusForDto(app),
                app.getInterviewCancelReason(),
                candidateView ? null : app.getAdvanceNote(),
                app.getAssignedHmUserId(),
                app.getSubmittedAt(),
                app.getUpdatedAt(),
                deadline,
                o.getResponseSlaDays(),
                remaining,
                breached,
                pace.labelled() ? pace.medianDays() : null,
                pace.badge(),
                app.getCvSections(),
                app.getCvStorageName() != null && !app.getCvStorageName().isBlank(),
                compat,
                app.getApplicationDetails(),
                app.getMatchScore(),
                currentScore,
                compat != null,
                interviews.planningState(app)
        );
    }

    /** Affichage DTO : ne pas inventer SCHEDULED sur un dossier déjà refusé/retiré/embauché. */
    private static String interviewStatusForDto(Application app) {
        if (app.getInterviewAt() == null) return null;
        if (app.getInterviewStatus() != null) return app.getInterviewStatus();
        ApplicationStatus st = app.getStatus();
        if (st == ApplicationStatus.REJECTED || st == ApplicationStatus.WITHDRAWN || st == ApplicationStatus.HIRED) {
            return InterviewService.CANCELLED;
        }
        return InterviewService.SCHEDULED;
    }

    /**
     * Poser ou reporter un entretien (étape Entretien). Autorisé aussi après annulation
     * (nouvelle planification explicite). L'ancien créneau est conservé dans la timeline.
     */
    @Transactional
    public Map<String, Object> rescheduleInterview(User actor, Long appId, ApiDtos.InterviewRequest req) {
        Application app = interviewApplication(actor, appId);
        boolean etaitPlanifie = interviews.isPlanned(app);
        boolean afterCancel = InterviewService.CANCELLED.equals(app.getInterviewStatus());
        Map<String, Object> previous = interviews.snapshot(app);
        interviews.schedule(app, req);
        applicationRepository.save(app);
        String action = etaitPlanifie ? "INTERVIEW_RESCHEDULED" : "INTERVIEW_PLANNED";
        audit.ok(action, actor, "APPLICATION", app.getId(), interviews.summary(app));
        String payload = interviewTimelinePayload(previous, interviews.snapshot(app),
                etaitPlanifie ? "RESCHEDULED" : (afterCancel ? "REPLANNED_AFTER_CANCEL" : "PLANNED"));
        timelineService.append(app.getId(),
                etaitPlanifie ? "INTERVIEW_SCHEDULED" : (afterCancel ? "INTERVIEW_CANCELLED" : null),
                etaitPlanifie ? "INTERVIEW_RESCHEDULED" : "INTERVIEW_PLANNED",
                actor.getId(), payload);
        notifier.interviewScheduled(app, etaitPlanifie);
        return interviewActionResult(app, etaitPlanifie ? "RESCHEDULED" : "PLANNED", previous);
    }

    @Transactional
    public Map<String, Object> cancelInterview(User actor, Long appId, String reason) {
        Application app = interviewApplication(actor, appId);
        Map<String, Object> previous = interviews.snapshot(app);
        interviews.cancel(app, reason);
        applicationRepository.save(app);
        audit.ok("INTERVIEW_CANCELLED", actor, "APPLICATION", app.getId(), reason);
        String payload = interviewTimelinePayload(previous, interviews.snapshot(app), "CANCELLED");
        timelineService.append(app.getId(), "INTERVIEW_SCHEDULED", "INTERVIEW_CANCELLED", actor.getId(), payload);
        notifier.interviewCancelled(app);
        return interviewActionResult(app, "CANCELLED", previous);
    }

    private Map<String, Object> interviewActionResult(Application app, String action, Map<String, Object> previous) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("application", toDto(app));
        out.put("action", action);
        out.put("previousSlot", previous);
        out.put("mailDelivery", mail.deliveryHint(app.getCandidate(), MailService.Kind.INTERVIEW));
        out.put("mailDeliveryLabel", mailDeliveryLabel(String.valueOf(out.get("mailDelivery"))));
        out.put("inAppNotification", true);
        return out;
    }

    private static String mailDeliveryLabel(String code) {
        return switch (code) {
            case "SMTP_OFF" -> "Notification interne créée. E-mail non envoyé (SMTP désactivé) — journalisé.";
            case "DISABLED_BY_USER" -> "Notification interne créée. E-mail non envoyé (préférence candidat).";
            case "QUEUED" -> "Notification interne créée. E-mail mis en file d'envoi (pas encore confirmé).";
            default -> "Notification interne créée.";
        };
    }

    private static String interviewTimelinePayload(Map<String, Object> previous, Map<String, Object> current, String kind) {
        return "{\"kind\":\"" + kind + "\",\"previous\":" + toJsonLoose(previous)
                + ",\"current\":" + toJsonLoose(current) + "}";
    }

    private static String toJsonLoose(Map<String, Object> m) {
        if (m == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(jsonEsc(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append('"').append(jsonEsc(String.valueOf(v))).append('"');
        }
        return sb.append('}').toString();
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
        if ("CANCELLED".equals(app.getInterviewStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cet entretien a été annulé.");
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

    /** Réponses facultatives du candidat : JSON objet uniquement, longueur bornée, sinon ignorées. */
    private static String cleanDetails(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty() || t.length() > 4000 || !t.startsWith("{") || !t.endsWith("}")) return null;
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(t);
            return t;
        } catch (Exception e) {
            return null;
        }
    }
}
