package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.OfferReport;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.Tag;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OfferReportRepository;
import ma.ensat.hirelink.repository.TagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final MembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final TagRepository tagRepository;
    private final SkillCatalogService skillCatalog;
    private final OfferTermsService offerTerms;
    private final TextMatcher textMatcher;

    public JobOfferService(
            JobOfferRepository jobOfferRepository,
            SkillMatchingService matchingService,
            ApplicationRepository applicationRepository,
            CompanyVerificationService companyVerificationService,
            FraudOfferScanService fraudOfferScanService,
            OfferReportRepository offerReportRepository,
            NotificationService notificationService,
            MembershipRepository membershipRepository,
            OrganizationService organizationService,
            TagRepository tagRepository,
            SkillCatalogService skillCatalog,
            OfferTermsService offerTerms,
            TextMatcher textMatcher
    ) {
        this.tagRepository = tagRepository;
        this.skillCatalog = skillCatalog;
        this.offerTerms = offerTerms;
        this.textMatcher = textMatcher;
        this.organizationService = organizationService;
        this.jobOfferRepository = jobOfferRepository;
        this.matchingService = matchingService;
        this.applicationRepository = applicationRepository;
        this.companyVerificationService = companyVerificationService;
        this.fraudOfferScanService = fraudOfferScanService;
        this.offerReportRepository = offerReportRepository;
        this.notificationService = notificationService;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.JobOfferResponse> listPublic(User viewer) {
        Stream<ApiDtos.JobOfferResponse> stream = jobOfferRepository
                .findByPublishedTrueAndSuspendedFalse()
                .stream()
                .filter(o -> !isExpired(o))
                .map(o -> toDto(o, viewer));
        if (viewer != null && viewer.getRole() == Role.CANDIDAT) {
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
        List<Long> authors = recruiter.getRole() == Role.ADMINISTRATEUR
                ? List.of(recruiter.getId())
                : organizationService.orgMemberIds(recruiter);
        return jobOfferRepository.findByRecruiterIdIn(authors).stream()
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
        if ((!o.isPublished() || o.isSuspended()) && !owner && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Offre non publique");
        }
        return toDto(o, viewer);
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
        applyWithFraudGate(o, req, wantPublish);
        jobOfferRepository.save(o);
        return toDto(o, null);
    }

    @Transactional
    public ApiDtos.JobOfferResponse update(User recruiter, Long id, ApiDtos.JobOfferRequest req) {
        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!organizationService.canManageOffer(recruiter, o)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pas propriétaire");
        }
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
        return toDto(o, null);
    }

    @Transactional
    public void delete(User recruiter, Long id) {
        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!organizationService.canManageOffer(recruiter, o)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pas propriétaire");
        }
        // Cohérence recruteur ↔ candidat : on ne supprime jamais l'historique d'un candidat.
        if (!applicationRepository.findByJobOfferId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette offre a déjà des candidatures : dépubliez-la plutôt que de la supprimer (les candidats gardent leur historique).");
        }
        jobOfferRepository.delete(o);
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
            notificationService.push(o.getRecruiter().getId(),
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
        if (req.responseSlaDays() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SLA ≥ 1 jour requis");
        }
        o.setTitle(req.title().trim());
        o.setDescription(req.description().trim());
        o.setRequiredSkills(skillCatalog.normaliseList(req.requiredSkills(), 500));
        o.setTags(resolveTags(o.getRequiredSkills()));
        o.setLocation(req.location());
        o.setResponseSlaDays(req.responseSlaDays());
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

    private ApiDtos.JobOfferResponse toDto(JobOffer o, User viewer) {
        Double match = null;
        List<String> matched = List.of();
        List<String> missing = List.of();
        if (viewer != null && viewer.getRole() == Role.CANDIDAT) {
            var detail = matchingService.explain(viewer.getSkills(), o.getRequiredSkills());
            match = Math.min(100.0, Math.round((detail.score() + preferenceBoost(viewer, o)) * 100.0) / 100.0);
            matched = detail.covered();
            missing = detail.missing();
        }
        return new ApiDtos.JobOfferResponse(
                o.getId(),
                o.getTitle(),
                o.getDescription(),
                o.getRequiredSkills(),
                o.getLocation(),
                o.getContractType(),
                o.getResponseSlaDays(),
                o.isPublished(),
                o.isSuspended(),
                o.getReportCount(),
                o.getQuarantineReason(),
                o.getRecruiter().getId(),
                o.getRecruiter().getFullName(),
                o.getCreatedAt(),
                match,
                companyName(o),
                companyVerified(o),
                companyLogo(o),
                companyWebsite(o),
                companySector(o),
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
                match != null && match >= PROFILE_MATCH_THRESHOLD
        );
    }

    private static boolean isExpired(JobOffer o) {
        return o.getDeadline() != null && o.getDeadline().isBefore(java.time.LocalDate.now());
    }

    private Organization orgOf(JobOffer o) {
        return membershipRepository.findByUserIdAndActiveTrue(o.getRecruiter().getId()).stream()
                .map(Membership::getOrganization)
                .findFirst()
                .orElse(null);
    }

    private String companyName(JobOffer o) {
        return organizationService.companyNameOf(o);
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

    private double preferenceBoost(User viewer, JobOffer o) {
        double boost = 0;
        String hayTitle = safe(o.getTitle()) + " " + safe(o.getDescription());
        if (tokenOverlap(viewer.getSoughtRoles(), hayTitle)) boost += 8;
        if (tokenOverlap(viewer.getPreferredLocations(), safe(o.getLocation()))) boost += 6;
        if (tokenOverlap(viewer.getPreferredContracts(), safe(o.getContractType()))) boost += 6;
        return boost;
    }

    private boolean tokenOverlap(String csv, String haystack) {
        if (csv == null || csv.isBlank() || haystack == null || haystack.isBlank()) return false;
        return Arrays.stream(csv.split("[,;|/]+"))
                .map(String::trim)
                .filter(t -> t.length() >= 2)
                .anyMatch(t -> textMatcher.contains(t, haystack));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
