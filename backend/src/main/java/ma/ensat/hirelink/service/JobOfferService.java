package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.OfferReport;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.Tag;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.OfferReportRepository;
import ma.ensat.hirelink.repository.TagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Offres + gate anti-fraude LinkedIn-like :
 * inscription libre ≠ publication libre (companyVerified).
 * Scan fake-job + signalement communautaire (≥3 → suspend).
 */
@Service
public class JobOfferService {

    public static final int REPORT_SUSPEND_THRESHOLD = 3;
    /** Score à partir duquel une offre « correspond au profil » du candidat. */
    public static final double PROFILE_MATCH_THRESHOLD = 50.0;

    private final JobOfferRepository jobOfferRepository;
    private final SkillMatchingService matchingService;
    private final ApplicationRepository applicationRepository;
    private final CompanyVerificationService companyVerificationService;
    private final FraudOfferScanService fraudOfferScanService;
    private final OfferReportRepository offerReportRepository;
    private final NotificationService notificationService;
    private final OrganizationService organizationService;
    private final TagRepository tagRepository;
    private final SkillCatalogService skillCatalog;
    private final OfferTermsService offerTerms;
    private final TextMatcher textMatcher;
    private final ResponsivenessService responsiveness;
    private final PlaceCatalogService placeCatalog;
    private final CompanyFollowService companyFollowService;
    private final AuditService audit;

    public JobOfferService(
            JobOfferRepository jobOfferRepository,
            SkillMatchingService matchingService,
            ApplicationRepository applicationRepository,
            CompanyVerificationService companyVerificationService,
            FraudOfferScanService fraudOfferScanService,
            OfferReportRepository offerReportRepository,
            NotificationService notificationService,
            OrganizationService organizationService,
            TagRepository tagRepository,
            SkillCatalogService skillCatalog,
            OfferTermsService offerTerms,
            TextMatcher textMatcher,
            PlaceCatalogService placeCatalog,
            ResponsivenessService responsiveness,
            CompanyFollowService companyFollowService,
            AuditService audit
    ) {
        this.tagRepository = tagRepository;
        this.skillCatalog = skillCatalog;
        this.offerTerms = offerTerms;
        this.textMatcher = textMatcher;
        this.placeCatalog = placeCatalog;
        this.responsiveness = responsiveness;
        this.companyFollowService = companyFollowService;
        this.organizationService = organizationService;
        this.jobOfferRepository = jobOfferRepository;
        this.matchingService = matchingService;
        this.applicationRepository = applicationRepository;
        this.companyVerificationService = companyVerificationService;
        this.fraudOfferScanService = fraudOfferScanService;
        this.offerReportRepository = offerReportRepository;
        this.notificationService = notificationService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.JobOfferResponse> listPublic(User viewer) {
        Stream<ApiDtos.JobOfferResponse> stream = jobOfferRepository
                .findByPublishedTrueAndSuspendedFalse()
                .stream()
                .filter(o -> !isExpired(o) && !isHiddenFromCandidates(o))
                .map(o -> toDto(o, viewer, false));
        if (viewer != null && viewer.getRole() == Role.CANDIDAT && matchingService.hasConfirmedSkills(viewer)) {
            return stream
                    .sorted(Comparator.comparingDouble((ApiDtos.JobOfferResponse o) ->
                            o.matchScore() != null ? o.matchScore() : 0.0).reversed())
                    .toList();
        }
        return stream.toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.JobOfferResponse> listMine(User recruiter) {
        // Offres de toute l'organisation (R02) : un collègue voit et pilote les mêmes offres que le pipeline.
        // L'appartenance gravée sur l'offre fait foi ; les auteurs membres restent inclus pour les offres
        // antérieures à cette colonne.
        if (recruiter.getRole() == Role.ADMINISTRATEUR) {
            return sorted(jobOfferRepository.findByRecruiterId(recruiter.getId()));
        }
        List<Long> authors = organizationService.orgMemberIds(recruiter);
        Organization org = organizationService.currentOrganization(recruiter);
        java.util.Map<Long, JobOffer> byId = new java.util.LinkedHashMap<>();
        if (org != null) {
            for (JobOffer o : jobOfferRepository.findByOrganizationId(org.getId())) byId.put(o.getId(), o);
        }
        for (JobOffer o : jobOfferRepository.findByRecruiterIdIn(authors)) byId.put(o.getId(), o);
        return sorted(List.copyOf(byId.values()));
    }

    private List<ApiDtos.JobOfferResponse> sorted(List<JobOffer> offers) {
        return offers.stream()
                .sorted(Comparator.comparing(JobOffer::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(o -> toDto(o, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.JobOfferResponse get(Long id, User viewer) {
        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        boolean owner = viewer != null && organizationService.canManageOffer(viewer, o);
        boolean admin = viewer != null && viewer.getRole() == Role.ADMINISTRATEUR;
        if (!isOpenToCandidates(o) && !owner && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Offre non publique");
        }
        return toDto(o, viewer, owner);
    }

    @Transactional
    public ApiDtos.JobOfferResponse create(User recruiter, ApiDtos.JobOfferRequest req) {
        requireRecruiter(recruiter);
        boolean wantPublish = req.published();
        if (wantPublish) {
            assertCanPublish(recruiter);
        }
        JobOffer o = new JobOffer();
        o.setRecruiter(recruiter);
        // Le propriétaire de l'offre est gravé à la création (A1) : il ne suit plus les mouvements de
        // l'auteur entre deux organisations. Un administrateur sans espace entreprise n'en attache aucun.
        o.setOrganization(organizationService.currentOrganization(recruiter));
        applyWithFraudGate(o, req, wantPublish);
        jobOfferRepository.save(o);
        audit.ok(wantPublish ? "OFFER_PUBLISHED" : "OFFER_CREATED", recruiter, "OFFER", o.getId(), o.getTitle());
        if (o.isPublished()) companyFollowService.announceNewOffer(o);
        return toDto(o, null);
    }

    @Transactional
    public ApiDtos.JobOfferResponse update(User recruiter, Long id, ApiDtos.JobOfferRequest req) {
        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!organizationService.canManageOffer(recruiter, o)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pas propriétaire");
        }
        boolean wasPublished = o.isPublished();
        boolean wantPublish = req.published();
        if (wantPublish && recruiter.getRole() != Role.ADMINISTRATEUR) {
            assertCanPublish(recruiter);
        }
        if (o.isSuspended() && wantPublish && recruiter.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Offre suspendue après signalements — contactez un administrateur");
        }
        applyWithFraudGate(o, req, wantPublish);
        jobOfferRepository.save(o);
        audit.ok("OFFER_UPDATED", recruiter, "OFFER", o.getId(), o.getTitle());
        if (o.isPublished() && !wasPublished) companyFollowService.announceNewOffer(o);
        return toDto(o, null);
    }

    @Transactional
    public void delete(User recruiter, Long id) {
        JobOffer o = requireManaged(recruiter, id);
        // Cohérence recruteur ↔ candidat : on ne supprime jamais l'historique d'un candidat.
        if (!applicationRepository.findByJobOfferId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette offre a déjà des candidatures : archivez-la pour la retirer de votre espace sans effacer l'historique des candidats.");
        }
        String titre = o.getTitle();
        jobOfferRepository.delete(o);
        audit.ok("OFFER_DELETED", recruiter, "OFFER", id, titre);
    }

    /** Publier ne veut pas dire « enregistrer de nouveau » : on ne rejoue pas tout le formulaire pour un interrupteur. */
    @Transactional
    public ApiDtos.JobOfferResponse publish(User recruiter, Long id) {
        JobOffer o = requireManaged(recruiter, id);
        if (o.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette offre est archivée : commencez par la restaurer.");
        }
        if (o.getClosedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette offre est clôturée : rouvrez les candidatures avant de la publier.");
        }
        if (o.isSuspended() && recruiter.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Offre suspendue après signalements — contactez un administrateur");
        }
        if (o.isPublished()) return toDto(o, null);
        if (recruiter.getRole() != Role.ADMINISTRATEUR) assertCanPublish(recruiter);
        var scan = fraudOfferScanService.scan(o.getTitle(), o.getDescription());
        if (scan.blockPublish()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, scan.message());
        }
        if (scan.softFlag()) {
            o.setQuarantineReason(scan.reasonCode() + ": " + String.join(", ", scan.hits()));
            jobOfferRepository.save(o);
            return toDto(o, null);
        }
        o.setPublished(true);
        o.setQuarantineReason(null);
        jobOfferRepository.save(o);
        audit.ok("OFFER_PUBLISHED", recruiter, "OFFER", o.getId(), o.getTitle());
        companyFollowService.announceNewOffer(o);
        return toDto(o, null);
    }

    @Transactional
    public ApiDtos.JobOfferResponse unpublish(User recruiter, Long id) {
        JobOffer o = requireManaged(recruiter, id);
        boolean etaitPubliee = o.isPublished();
        o.setPublished(false);
        jobOfferRepository.save(o);
        if (etaitPubliee) {
            audit.ok("OFFER_UNPUBLISHED", recruiter, "OFFER", o.getId(), o.getTitle());
        }
        return toDto(o, null);
    }

    /**
     * Clôturer arrête la collecte de candidatures sans rien effacer : l'offre garde ses dossiers,
     * le recruteur garde la preuve du recrutement réalisé.
     */
    @Transactional
    public ApiDtos.JobOfferResponse close(User recruiter, Long id) {
        JobOffer o = requireManaged(recruiter, id);
        boolean etaitOuverte = o.getClosedAt() == null;
        if (etaitOuverte) o.setClosedAt(Instant.now());
        jobOfferRepository.save(o);
        if (etaitOuverte) {
            audit.ok("OFFER_CLOSED", recruiter, "OFFER", o.getId(), o.getTitle());
        }
        return toDto(o, null);
    }

    @Transactional
    public ApiDtos.JobOfferResponse reopen(User recruiter, Long id) {
        JobOffer o = requireManaged(recruiter, id);
        if (o.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette offre est archivée : commencez par la restaurer.");
        }
        o.setClosedAt(null);
        jobOfferRepository.save(o);
        audit.ok("OFFER_REOPENED", recruiter, "OFFER", o.getId(), o.getTitle());
        return toDto(o, null);
    }

    @Transactional
    public ApiDtos.JobOfferResponse archive(User recruiter, Long id) {
        JobOffer o = requireManaged(recruiter, id);
        boolean etaitActive = o.getArchivedAt() == null;
        if (etaitActive) o.setArchivedAt(Instant.now());
        jobOfferRepository.save(o);
        if (etaitActive) {
            audit.ok("OFFER_ARCHIVED", recruiter, "OFFER", o.getId(), o.getTitle());
        }
        return toDto(o, null);
    }

    @Transactional
    public ApiDtos.JobOfferResponse unarchive(User recruiter, Long id) {
        JobOffer o = requireManaged(recruiter, id);
        o.setArchivedAt(null);
        jobOfferRepository.save(o);
        audit.ok("OFFER_UNARCHIVED", recruiter, "OFFER", o.getId(), o.getTitle());
        return toDto(o, null);
    }

    /** Relancer une recherche : on repart du texte et des conditions, jamais du compteur de candidatures. */
    @Transactional
    public ApiDtos.JobOfferResponse duplicate(User recruiter, Long id) {
        JobOffer src = requireManaged(recruiter, id);
        JobOffer copy = new JobOffer();
        copy.setRecruiter(recruiter);
        copy.setOrganization(src.getOrganization());
        copy.setTitle(src.getTitle());
        copy.setDescription(src.getDescription());
        copy.setRequiredSkills(src.getRequiredSkills());
        copy.setNiceToHaveSkills(src.getNiceToHaveSkills());
        copy.setTags(new LinkedHashSet<>(src.getTags()));
        copy.setLocation(src.getLocation());
        copy.setContractType(src.getContractType());
        copy.setResponseSlaDays(src.getResponseSlaDays());
        copy.setCompensationType(src.getCompensationType());
        copy.setSalaryMin(src.getSalaryMin());
        copy.setSalaryMax(src.getSalaryMax());
        copy.setSalaryPeriod(src.getSalaryPeriod());
        copy.setSalaryBasis(src.getSalaryBasis());
        copy.setCurrency(src.getCurrency());
        copy.setDurationMonths(src.getDurationMonths());
        copy.setStartDate(src.getStartDate());
        copy.setStartAsap(src.getStartAsap());
        copy.setAgreementRequired(src.getAgreementRequired());
        copy.setHiringPossible(src.getHiringPossible());
        copy.setProbationMonths(src.getProbationMonths());
        copy.setSchedule(src.getSchedule());
        copy.setWorkMode(src.getWorkMode());
        copy.setExperienceLevel(src.getExperienceLevel());
        copy.setEducationLevel(src.getEducationLevel());
        copy.setPositions(src.getPositions());
        copy.setBenefits(src.getBenefits());
        // une copie ne renaît pas avec une date limite déjà dépassée
        copy.setDeadline(src.getDeadline() != null && src.getDeadline().isAfter(BusinessTime.today()) ? src.getDeadline() : null);
        copy.setPublished(false);
        jobOfferRepository.save(copy);
        audit.ok("OFFER_DUPLICATED", recruiter, "OFFER", copy.getId(), "copie de l'offre " + id + " — " + copy.getTitle());
        return toDto(copy, null);
    }

    /** Prolonger depuis la date en cours : sinon « +7 jours » sur une offre expirée repartirait de hier. */
    @Transactional
    public ApiDtos.JobOfferResponse extendDeadline(User recruiter, Long id, Integer days) {
        JobOffer o = requireManaged(recruiter, id);
        int add = days == null ? 0 : days;
        if (add < 1 || add > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez une prolongation entre 1 et 180 jours.");
        }
        LocalDate base = o.getDeadline() != null && o.getDeadline().isAfter(BusinessTime.today()) ? o.getDeadline() : BusinessTime.today();
        o.setDeadline(base.plusDays(add));
        jobOfferRepository.save(o);
        audit.ok("OFFER_DEADLINE_EXTENDED", recruiter, "OFFER", o.getId(), "+" + add + " j → " + o.getDeadline());
        return toDto(o, null);
    }

    /* --------------------------------------------------------------- modération */

    /** Suspendre après signalement confirmé : la vitrine s'éteint et le recruteur connaît le motif. */
    @Transactional
    public ApiDtos.JobOfferResponse adminSuspend(User admin, Long id, String reason) {
        JobOffer o = requireModeration(admin, id);
        if (o.isSuspended()) return toDto(o, null);
        String why = reason == null || reason.isBlank() ? "Signalement confirmé par la modération" : reason.trim();
        o.setSuspended(true);
        o.setPublished(false);
        o.setQuarantineReason("MODERATION: " + why);
        jobOfferRepository.save(o);
        resolveReports(o.getId(), "FONDE", admin.getEmail());
        notificationService.push("offer:" + o.getId(), o.getRecruiter().getId(), "Offre suspendue",
                "« " + o.getTitle() + " » a été suspendue par la modération : " + why, "MODERATION");
        return toDto(o, null);
    }

    /** Rétablir est la seule sortie de suspension : sans elle, une offre arrêtée par erreur l'est pour toujours. */
    @Transactional
    public ApiDtos.JobOfferResponse adminRestore(User admin, Long id) {
        JobOffer o = requireModeration(admin, id);
        o.setSuspended(false);
        o.setQuarantineReason(null);
        jobOfferRepository.save(o);
        resolveReports(o.getId(), "ECARTE", admin.getEmail());
        notificationService.push("offer:" + o.getId(), o.getRecruiter().getId(), "Offre rétablie",
                "« " + o.getTitle() + " » n'est plus suspendue. Publiez-la de nouveau pour la remettre en vitrine.", "MODERATION");
        return toDto(o, null);
    }

    /** Retirer une offre de la vitrine sans la sanctionner : le signalement n'est pas confirmé, mais le doute suffit. */
    @Transactional
    public ApiDtos.JobOfferResponse adminUnpublish(User admin, Long id) {
        JobOffer o = requireModeration(admin, id);
        o.setPublished(false);
        jobOfferRepository.save(o);
        notificationService.push("offer:" + o.getId(), o.getRecruiter().getId(), "Offre retirée de la vitrine",
                "« " + o.getTitle() + " » n'apparaît plus dans la recherche. Vous pouvez la publier à nouveau.", "MODERATION");
        return toDto(o, null);
    }

    /** Écarter les signalements d'une offre : rien n'est touché, la file se vide — c'est la réponse « hors sujet ». */
    @Transactional
    public int adminDismissReports(User admin, Long id) {
        JobOffer o = requireModeration(admin, id);
        return resolveReports(o.getId(), "ECARTE", admin.getEmail());
    }

    private JobOffer requireModeration(User admin, Long id) {
        if (admin == null || admin.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modération réservée aux administrateurs");
        }
        return jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
    }

    /**
     * Les signalements d'une même offre se traitent ensemble : le sujet de la file est l'offre, pas l'alerte.
     * Une décision déjà prise n'est jamais réécrite — suspendre puis rétablier laisse les deux traces.
     */
    private int resolveReports(Long offerId, String decision, String handlerEmail) {
        int handled = 0;
        for (OfferReport r : offerReportRepository.findByJobOfferIdOrderByCreatedAtAsc(offerId)) {
            if (r.getResolvedAt() != null) continue;
            r.setStatus(decision);
            r.setResolvedAt(Instant.now());
            r.setHandledByEmail(handlerEmail);
            offerReportRepository.save(r);
            handled++;
        }
        return handled;
    }

    private JobOffer requireManaged(User recruiter, Long id) {        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!organizationService.canManageOffer(recruiter, o)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pas propriétaire");
        }
        return o;
    }

    /** Une offre close ou rangée n'est plus une vitrine, même si elle reste publiée côté entreprise. */
    private static boolean isHiddenFromCandidates(JobOffer o) {
        return o.getClosedAt() != null || o.getArchivedAt() != null;
    }

    private static boolean isOpenToCandidates(JobOffer o) {
        return o.isPublished() && !o.isSuspended() && !isHiddenFromCandidates(o);
    }

    /** Signalement communautaire — 3 alertes → suspension auto. */
    @Transactional
    public Map<String, Object> report(User reporter, Long offerId, String reason) {
        if (reporter.getRole() != Role.CANDIDAT && reporter.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Signalement réservé aux candidats");
        }
        JobOffer o = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!o.isPublished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offre non publiée");
        }
        if (offerReportRepository.findByJobOfferIdAndReporterUserId(offerId, reporter.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez déjà signalé cette offre");
        }
        OfferReport r = new OfferReport();
        r.setJobOfferId(offerId);
        r.setReporterUserId(reporter.getId());
        r.setReason(reason == null || reason.isBlank() ? "SIGNALEMENT" : reason.trim());
        offerReportRepository.save(r);

        int count = (int) offerReportRepository.countByJobOfferId(offerId);
        o.setReportCount(count);
        boolean suspendedNow = false;
        if (count >= REPORT_SUSPEND_THRESHOLD) {
            o.setSuspended(true);
            o.setPublished(false);
            o.setQuarantineReason("SUSPENDED_BY_REPORTS (" + count + ")");
            suspendedNow = true;
            notificationService.push("offer:" + o.getId(), o.getRecruiter().getId(),
                    "Offre suspendue",
                    "« " + o.getTitle() + " » a été suspendue après " + count + " signalements.",
                    "MODERATION");
        }
        jobOfferRepository.save(o);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offerId", offerId);
        m.put("reportCount", count);
        m.put("threshold", REPORT_SUSPEND_THRESHOLD);
        m.put("suspended", o.isSuspended());
        m.put("suspendedNow", suspendedNow);
        m.put("status", suspendedNow ? "SUSPENDED" : "REPORTED");
        return m;
    }

    /** Middleware logique : Inscription libre ≠ Publication libre. */
    private void assertCanPublish(User recruiter) {
        if (recruiter.getRole() == Role.ADMINISTRATEUR) return;
        if (recruiter.isCompanyVerified() || organizationService.isOrgVerified(recruiter)) return;
        String status = recruiter.getVerificationStatus() == null ? "PENDING" : recruiter.getVerificationStatus();
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                companyVerificationService.publishBlockReason(status));
    }

    /** Les compétences demandées alimentent la relation N–N JobOffer ↔ Tag (un tag partagé entre offres). */
    private Set<Tag> resolveTags(String skills) {
        Set<Tag> tags = new LinkedHashSet<>();
        if (skills == null) return tags;
        for (String raw : skills.split("[,;]")) {
            String name = raw.trim();
            if (name.isEmpty() || name.length() > 64 || name.contains("(")) continue; // les langues « Français (Natif) » ne sont pas des tags
            tags.add(tagRepository.findByNameIgnoreCase(name).orElseGet(() -> {
                Tag t = new Tag();
                t.setName(name);
                return tagRepository.save(t);
            }));
        }
        return tags;
    }

    private void applyWithFraudGate(JobOffer o, ApiDtos.JobOfferRequest req, boolean wantPublish) {
        o.setTitle(req.title().trim());
        o.setDescription(req.description().trim());
        o.setRequiredSkills(skillCatalog.normaliseList(req.requiredSkills(), 500));
        o.setNiceToHaveSkills(req.niceToHaveSkills() == null || req.niceToHaveSkills().isBlank()
                ? null : skillCatalog.normaliseList(req.niceToHaveSkills(), 500));
        o.setTags(resolveTags(o.getRequiredSkills()));
        o.setLocation(normalizeLocation(req.location(), o));
        if (req.responseSlaDays() != null && req.responseSlaDays() >= 1) {
            o.setResponseSlaDays(req.responseSlaDays());
        }
        offerTerms.apply(o, req);

        var scan = fraudOfferScanService.scan(req.title(), req.description());
        if (wantPublish && scan.blockPublish()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, scan.message());
        }
        if (wantPublish && scan.softFlag()) {
            o.setPublished(false);
            o.setQuarantineReason(scan.reasonCode() + ": " + String.join(", ", scan.hits()));
        } else {
            o.setPublished(wantPublish && !o.isSuspended());
            if (wantPublish) {
                o.setQuarantineReason(null);
            }
        }
    }

    private void requireRecruiter(User u) {
        if (u.getRole() != Role.RECRUTEUR && u.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
    }

    /** Vue « interne » : le board du recruteur et les actions de modération voient l'état réel de l'offre. */
    private ApiDtos.JobOfferResponse toDto(JobOffer o, User viewer) {
        return toDto(o, viewer, true);
    }

    /**
     * A6 : suspension, compteur de signalements et motif de quarantaine sont des informations de
     * modération. Elles ne sortent que vers un gestionnaire de l'offre ou un administrateur ; la vitrine
     * publique et le tableau de bord d'une autre organisation reçoivent ces champs neutres.
     */
    private ApiDtos.JobOfferResponse toDto(JobOffer o, User viewer, boolean moderationVisible) {
        Double match = null;
        List<String> matched = List.of();
        List<String> missing = List.of();
        SkillMatchingService.Compatibility breakdown = null;
        if (viewer != null && viewer.getRole() == Role.CANDIDAT) {
            breakdown = matchingService.evaluateIfAvailable(viewer, o);
            if (breakdown != null) {
                match = breakdown.score();
                matched = breakdown.covered();
                missing = breakdown.missing();
            }
        }
        ResponsivenessService.Signal pace = responsiveness.signalOf(o.getRecruiter().getAssignedOrgId());
        return new ApiDtos.JobOfferResponse(
                o.getId(),
                o.getTitle(),
                o.getDescription(),
                o.getRequiredSkills(),
                o.getLocation(),
                o.getContractType(),
                o.getResponseSlaDays(),
                o.isPublished(),
                moderationVisible && o.isSuspended(),
                moderationVisible ? o.getReportCount() : 0,
                moderationVisible ? o.getQuarantineReason() : null,
                o.getRecruiter().getId(),
                o.getRecruiter().getFullName(),
                o.getCreatedAt(),
                match,
                companyName(o),
                companyCity(o),
                companyVerified(o),
                companyLogo(o),
                companyWebsite(o),
                companySector(o),
                pace.labelled() ? pace.medianDays() : null,
                pace.badge(),
                o.getCompensationType(),
                o.getSalaryMin(),
                o.getSalaryMax(),
                o.getSalaryPeriod(),
                o.getSalaryBasis(),
                o.getCurrency(),
                o.getDurationMonths(),
                o.getStartDate(),
                o.getStartAsap(),
                o.getAgreementRequired(),
                o.getHiringPossible(),
                o.getProbationMonths(),
                o.getSchedule(),
                o.getWorkMode(),
                o.getExperienceLevel(),
                o.getEducationLevel(),
                o.getPositions(),
                o.getDeadline(),
                o.getBenefits(),
                matched,
                missing,
                match != null && match >= PROFILE_MATCH_THRESHOLD,
                o.getNiceToHaveSkills(),
                breakdown,
                o.getClosedAt(),
                o.getArchivedAt()
        );
    }

    private static boolean isExpired(JobOffer o) {
        return o.getDeadline() != null && o.getDeadline().isBefore(BusinessTime.today());
    }

    private Organization orgOf(JobOffer o) {
        return organizationService.organizationOf(o);
    }

    private String companyName(JobOffer o) {
        return organizationService.companyNameOf(o);
    }

    /** Ville de l'entreprise : sert de repli quand l'offre n'a pas de ville exploitable (lieu vide, mode de travail ou pays). */
    private String companyCity(JobOffer o) {
        Organization org = orgOf(o);
        if (org != null && org.getCity() != null && !org.getCity().isBlank()) return org.getCity().trim();
        String own = o.getRecruiter().getCity();
        return own == null ? "" : own.trim();
    }

    private static final java.util.regex.Pattern MODE_WORD =
            java.util.regex.Pattern.compile(
                    "t[ée]l[ée]travail|remote|hybride|sur site|pr[ée]sentiel|[àa] distance|onsite|full[- ]?remote",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern MODE_HYBRID =
            java.util.regex.Pattern.compile("hybride", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern MODE_ONSITE =
            java.util.regex.Pattern.compile("sur site|pr[ée]sentiel|onsite", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static String canonicalMode(String segment) {
        if (MODE_HYBRID.matcher(segment).find()) return "Hybride";
        if (MODE_ONSITE.matcher(segment).find()) return "Sur site";
        return "Télétravail";
    }

    /**
     * « Maroc » n'est pas une ville. Un pays saisi comme lieu est écarté au profit de la ville de
     * l'entreprise ; le pays se déduit de cette ville à l'affichage, il n'est jamais stocké à sa place.
     */
    private String normalizeLocation(String raw, JobOffer o) {
        String value = raw == null ? "" : raw.trim();
        String city = "";
        String mode = "";
        for (String segment : value.split("[·|,/]")) {
            String s = segment.trim();
            if (s.isEmpty()) continue;
            if (MODE_WORD.matcher(s).find()) mode = canonicalMode(s);
            else if (!placeCatalog.isCountry(s)) city = s;
        }
        if (city.isEmpty()) city = companyCity(o);
        if (city.isEmpty()) return value;
        return mode.isEmpty() ? city : city + " · " + mode;
    }

    private boolean companyVerified(JobOffer o) {
        Organization org = orgOf(o);
        return (org != null && org.isVerified()) || o.getRecruiter().isCompanyVerified();
    }

    private String companyLogo(JobOffer o) {
        Organization org = orgOf(o);
        if (org != null && org.getLogoUrl() != null && !org.getLogoUrl().isBlank()) return org.getLogoUrl();
        return CompanyDirectory.logoFor(org == null ? o.getRecruiter().getCompanyIce() : org.getIce(), companyName(o));
    }

    private String companyWebsite(JobOffer o) {
        Organization org = orgOf(o);
        return org == null || org.getWebsite() == null ? "" : org.getWebsite();
    }

    private String companySector(JobOffer o) {
        Organization org = orgOf(o);
        return org == null || org.getSector() == null ? "" : org.getSector();
    }



}
