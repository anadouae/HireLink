package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.OfferReport;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OfferReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private final JobOfferRepository jobOfferRepository;
    private final SkillMatchingService matchingService;
    private final ApplicationRepository applicationRepository;
    private final CompanyVerificationService companyVerificationService;
    private final FraudOfferScanService fraudOfferScanService;
    private final OfferReportRepository offerReportRepository;
    private final NotificationService notificationService;
    private final MembershipRepository membershipRepository;

    public JobOfferService(
            JobOfferRepository jobOfferRepository,
            SkillMatchingService matchingService,
            ApplicationRepository applicationRepository,
            CompanyVerificationService companyVerificationService,
            FraudOfferScanService fraudOfferScanService,
            OfferReportRepository offerReportRepository,
            NotificationService notificationService,
            MembershipRepository membershipRepository
    ) {
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
        return jobOfferRepository.findByRecruiterId(recruiter.getId()).stream()
                .map(o -> toDto(o, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.JobOfferResponse get(Long id, User viewer) {
        JobOffer o = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        boolean owner = viewer != null && viewer.getId().equals(o.getRecruiter().getId());
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
        if (!o.getRecruiter().getId().equals(recruiter.getId()) && recruiter.getRole() != Role.ADMINISTRATEUR) {
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
        if (!o.getRecruiter().getId().equals(recruiter.getId()) && recruiter.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pas propriétaire");
        }
        List<Application> apps = applicationRepository.findByJobOfferId(id);
        applicationRepository.deleteAll(apps);
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
        if (recruiter.isCompanyVerified()) return;
        String status = recruiter.getVerificationStatus() == null ? "PENDING" : recruiter.getVerificationStatus();
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                companyVerificationService.publishBlockReason(status));
    }

    private void applyWithFraudGate(JobOffer o, ApiDtos.JobOfferRequest req, boolean wantPublish) {
        if (req.responseSlaDays() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SLA ≥ 1 jour requis");
        }
        o.setTitle(req.title().trim());
        o.setDescription(req.description().trim());
        o.setRequiredSkills(req.requiredSkills());
        o.setLocation(req.location());
        o.setContractType(req.contractType());
        o.setResponseSlaDays(req.responseSlaDays());

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
        if (viewer != null && viewer.getRole() == Role.CANDIDAT) {
            double base = matchingService.computeMatchScore(viewer.getSkills(), o.getRequiredSkills());
            double boost = preferenceBoost(viewer, o);
            match = Math.min(100.0, Math.round((base + boost) * 100.0) / 100.0);
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
                companySector(o)
        );
    }

    private Organization orgOf(JobOffer o) {
        return membershipRepository.findByUserIdAndActiveTrue(o.getRecruiter().getId()).stream()
                .map(Membership::getOrganization)
                .findFirst()
                .orElse(null);
    }

    private String companyName(JobOffer o) {
        Organization org = orgOf(o);
        if (org != null && org.getName() != null && !org.getName().isBlank()) return org.getName();
        if (o.getRecruiter().getCompany() != null && !o.getRecruiter().getCompany().isBlank()) {
            return o.getRecruiter().getCompany();
        }
        return o.getRecruiter().getFullName();
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
        String hay = haystack.toLowerCase(Locale.ROOT);
        return Arrays.stream(csv.split("[,;|/]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(hay::contains);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
