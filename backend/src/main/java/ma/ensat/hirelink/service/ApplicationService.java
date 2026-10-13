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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline ATS — machine d'états R01, isolation org R02, checklist R05,
 * catalogue refus R04, retrait candidat R11, HM R14.
 * Périmètre produit : le parcours positif s'arrête à INTERVIEW
 * (OFFER / ACCEPTED hors gestion réelle — refus / retrait restent possibles).
 */
@Service
public class ApplicationService {

    private static final Set<ApplicationStatus> TERMINAL = EnumSet.of(
            ApplicationStatus.INTERVIEW, ApplicationStatus.ACCEPTED,
            ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN
    );

    private static final Set<ApplicationStatus> OUT_OF_SCOPE = EnumSet.of(
            ApplicationStatus.OFFER, ApplicationStatus.ACCEPTED
    );

    private static final int MAX_APPLY_PER_DAY = 25;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
            ApplicationStatus.SUBMITTED, EnumSet.of(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED),
            ApplicationStatus.SCREENING, EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
            ApplicationStatus.SHORTLISTED, EnumSet.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
            // Fin de parcours positif : INTERVIEW. Ensuite seulement rejet (ou retrait candidat).
            ApplicationStatus.INTERVIEW, EnumSet.of(ApplicationStatus.REJECTED)
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

    public ApplicationService(
            ApplicationRepository applicationRepository,
            JobOfferRepository jobOfferRepository,
            SkillMatchingService matchingService,
            TimelineService timelineService,
            RefusalReasonRepository refusalReasonRepository,
            MatchingAnalysisRepository matchingAnalysisRepository,
            NotificationService notificationService,
            OrganizationService organizationService,
            MembershipRepository membershipRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.matchingService = matchingService;
        this.timelineService = timelineService;
        this.refusalReasonRepository = refusalReasonRepository;
        this.matchingAnalysisRepository = matchingAnalysisRepository;
        this.notificationService = notificationService;
        this.organizationService = organizationService;
        this.membershipRepository = membershipRepository;
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
        Instant now = Instant.now();
        app.setSubmittedAt(now);
        app.setResponseDeadline(now.plus(offer.getResponseSlaDays(), ChronoUnit.DAYS));
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
                candidate.getId(), "{\"event\":\"APPLY\",\"match\":" + Math.round(score) + "}");
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

    /** Métriques funnel SaaS (conversion par étape). */
    @Transactional(readOnly = true)
    public Map<String, Object> pipelineFunnel(User recruiter) {
        List<ApiDtos.ApplicationResponse> apps = pipelineForRecruiter(recruiter);
        LinkedHashMap<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        long breached = 0;
        long hot = 0;
        for (ApiDtos.ApplicationResponse a : apps) {
            byStatus.merge(a.status().name(), 1L, Long::sum);
            if (a.slaBreached()) breached++;
            if (a.matchScore() != null && a.matchScore() >= 75) hot++;
        }
        long active = apps.stream().filter(a -> !TERMINAL.contains(a.status())).count();
        long decided = byStatus.getOrDefault("ACCEPTED", 0L) + byStatus.getOrDefault("REJECTED", 0L);
        long submitted = Math.max(1, apps.size());
        double conversionToInterview = 100.0 * (byStatus.getOrDefault("INTERVIEW", 0L)
                + byStatus.getOrDefault("OFFER", 0L)
                + byStatus.getOrDefault("ACCEPTED", 0L)) / submitted;
        double hireRate = 100.0 * byStatus.getOrDefault("ACCEPTED", 0L) / submitted;

        Map<String, Object> growth = new LinkedHashMap<>();
        growth.put("hint", "Réduire le temps en SCREENING et respecter le SLA augmente la conversion et le score de fiabilité.");
        if (breached > 0) {
            growth.put("nudge", "Upgrade Performance : alertes SLA + kit entretien IA pour traiter les " + breached + " dossiers en retard.");
        } else if (active > 8) {
            growth.put("nudge", "Volume élevé — scorecards F12 + assignation HM accélèrent la shortlist.");
        } else {
            growth.put("nudge", null);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", apps.size());
        m.put("active", active);
        m.put("decided", decided);
        m.put("slaBreached", breached);
        m.put("hotMatches", hot);
        m.put("byStatus", byStatus);
        m.put("conversionToInterviewPct", Math.round(conversionToInterview * 10) / 10.0);
        m.put("hireRatePct", Math.round(hireRate * 10) / 10.0);
        m.put("growth", growth);
        return m;
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
        timelineService.append(app.getId(), app.getStatus().name(), app.getStatus().name(),
                actor.getId(), "{\"event\":\"ASSIGN_HM\",\"hmUserId\":" + hmUserId + "}");
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

        if (OUT_OF_SCOPE.contains(req.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hors périmètre HireLink : le pipeline s'arrête à INTERVIEW (pas d'étape Offre / Accepté).");
        }

        if (req.status() == ApplicationStatus.WITHDRAWN) {
            if (!app.getCandidate().getId().equals(actor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le candidat peut retirer (R11)");
            }
            // Retrait possible tant que non rejeté / déjà retiré (INTERVIEW reste retirable).
            if (from == ApplicationStatus.REJECTED || from == ApplicationStatus.WITHDRAWN
                    || from == ApplicationStatus.ACCEPTED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Retrait impossible à cette étape (R11)");
            }
            app.setStatus(ApplicationStatus.WITHDRAWN);
            applicationRepository.save(app);
            timelineService.append(app.getId(), from.name(), ApplicationStatus.WITHDRAWN.name(),
                    actor.getId(), "{\"event\":\"WITHDRAW\"}");
            notificationService.push(app.getJobOffer().getRecruiter().getId(),
                    "Candidature retirée",
                    app.getCandidate().getFullName() + " a retiré sa candidature",
                    "STATUS");
            return toDto(app);
        }

        assertCanActOnPipeline(actor, app);

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
            applyRefusalCatalog(app, req.rejectionReason());
        }

        if (req.status() == ApplicationStatus.INTERVIEW) {
            applyInterviewGate(app, req);
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

        String payload = buildTimelinePayload(app, req);
        timelineService.append(app.getId(), from.name(), req.status().name(), actor.getId(), payload);
        notificationService.push(app.getCandidate().getId(),
                "Mise à jour candidature",
                "« " + app.getJobOffer().getTitle() + " » → " + req.status().name()
                        + (req.status() == ApplicationStatus.REJECTED && app.getRejectionReason() != null
                        ? " — " + app.getRejectionReason() : ""),
                "STATUS");
        if (app.getAssignedHmUserId() != null && !app.getAssignedHmUserId().equals(actor.getId())) {
            notificationService.push(app.getAssignedHmUserId(),
                    "Pipeline mis à jour",
                    app.getCandidate().getFullName() + " → " + req.status().name(),
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motif de refus obligatoire (R04)");
        }
        String reason = reasonRaw.trim();
        RefusalReason catalog = refusalReasonRepository.findByCode(reason)
                .or(() -> refusalReasonRepository.findByActiveTrue().stream()
                        .filter(rr -> rr.getLabel().equalsIgnoreCase(reason))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Motif hors catalogue (R04) — choisissez un code catalogue"));
        app.setRefusalReasonCode(catalog.getCode());
        app.setRejectionReason(catalog.getLabel());
    }

    private void applyInterviewGate(Application app, ApiDtos.AdvanceRequest req) {
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
        if (req.interviewAt().isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Créneau d’entretien dans le passé");
        }
        app.setInterviewType(type);
        app.setInterviewAt(req.interviewAt());
        if (req.interviewLocation() != null && !req.interviewLocation().isBlank()) {
            app.setInterviewLocation(req.interviewLocation().trim());
        }
        if (req.interviewNotes() != null && !req.interviewNotes().isBlank()) {
            String n = req.interviewNotes().trim();
            if (n.length() > 1000) n = n.substring(0, 1000);
            app.setInterviewNotes(n);
        }
    }

    private static String buildTimelinePayload(Application app, ApiDtos.AdvanceRequest req) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"rejectionReason\":\"").append(jsonEsc(app.getRejectionReason())).append("\"");
        sb.append(",\"refusalCode\":\"").append(jsonEsc(app.getRefusalReasonCode())).append("\"");
        if (req.interviewType() != null) {
            sb.append(",\"interviewType\":\"").append(jsonEsc(req.interviewType())).append("\"");
        }
        sb.append("}");
        return sb.toString();
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
        long remaining = 0;
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
                app.getPhotoFileName(),
                app.getInterviewType(),
                app.getInterviewAt(),
                app.getInterviewLocation(),
                app.getInterviewNotes(),
                app.getAdvanceNote(),
                app.getAssignedHmUserId(),
                app.getSubmittedAt(),
                app.getUpdatedAt(),
                deadline,
                o.getResponseSlaDays(),
                remaining,
                breached
        );
    }
}
