package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.service.BillingCatalogService;
import ma.ensat.hirelink.service.CompanyVerificationService;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.BillingInvoiceRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final JobOfferRepository jobOfferRepository;
    private final ApplicationRepository applicationRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final BillingCatalogService billingCatalogService;
    private final CompanyVerificationService companyVerificationService;
    private final BillingInvoiceRepository billingInvoiceRepository;

    public AdminController(
            UserRepository userRepository,
            JobOfferRepository jobOfferRepository,
            ApplicationRepository applicationRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            BillingCatalogService billingCatalogService,
            CompanyVerificationService companyVerificationService,
            BillingInvoiceRepository billingInvoiceRepository
    ) {
        this.userRepository = userRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.applicationRepository = applicationRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.billingCatalogService = billingCatalogService;
        this.companyVerificationService = companyVerificationService;
        this.billingInvoiceRepository = billingInvoiceRepository;
    }

    @GetMapping("/stats")
    public ApiDtos.AdminStats stats(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        return new ApiDtos.AdminStats(
                userRepository.count(),
                userRepository.countByRole(Role.CANDIDAT),
                userRepository.countByRole(Role.RECRUTEUR),
                jobOfferRepository.count(),
                applicationRepository.count(),
                jobOfferRepository.countByPublishedTrue()
        );
    }

    @GetMapping("/charts")
    public Map<String, Object> charts(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status.name(), applicationRepository.countByStatus(status));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationsByStatus", byStatus);
        return body;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("email", u.getEmail());
            row.put("fullName", u.getFullName());
            row.put("role", u.getRole().name());
            row.put("active", u.isActive());
            row.put("mustSetPassword", u.isMustSetPassword());
            row.put("emailVerified", u.isEmailVerified());
            rows.add(row);
        }
        return rows;
    }

    @PatchMapping("/users/{id}/active")
    public Map<String, Object> toggleActive(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        requireAdmin(admin);
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User introuvable"));
        u.setActive(Boolean.TRUE.equals(body.get("active")));
        userRepository.save(u);
        return Map.of("id", u.getId(), "active", u.isActive());
    }

    @PatchMapping("/users/{id}/verify-company")
    public Map<String, Object> verifyCompany(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id
    ) {
        requireAdmin(admin);
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User introuvable"));
        u.setCompanyVerified(true);
        u.setVerificationStatus("REGISTRY_MATCH");
        userRepository.save(u);
        return Map.of(
                "id", u.getId(),
                "companyVerified", true,
                "verificationStatus", u.getVerificationStatus(),
                "company", u.getCompany() == null ? "" : u.getCompany()
        );
    }

    @GetMapping("/organizations")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> organizations(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        return organizationRepository.findAllWithOwner().stream().map(this::orgSummary).toList();
    }

    @GetMapping("/organizations/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> organization(@AuthenticationPrincipal User user, @PathVariable Long id) {
        requireAdmin(user);
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
        Map<String, Object> body = orgSummary(org);
        List<Map<String, Object>> members = new ArrayList<>();
        for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(org.getId())) {
            User member = m.getUser();
            members.add(Map.of(
                    "userId", member.getId(),
                    "fullName", member.getFullName(),
                    "email", member.getEmail(),
                    "role", m.getOrgRole().name(),
                    "active", member.isActive()
            ));
        }
        body.put("members", members);
        return body;
    }

    @PatchMapping("/organizations/{id}")
    @Transactional
    public Map<String, Object> updateOrganization(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        requireAdmin(admin);
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
        if (body.containsKey("verified")) {
            boolean verified = Boolean.TRUE.equals(body.get("verified"));
            companyVerificationService.markAdminDecision(org, verified);
            for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(org.getId())) {
                if (m.getUser().getRole() == Role.RECRUTEUR) {
                    m.getUser().setCompanyVerified(verified);
                    m.getUser().setVerificationStatus(org.getVerificationStatus());
                    m.getUser().setCompany(org.getName());
                    m.getUser().setCompanyIce(org.getIce());
                }
            }
        }
        if (body.get("planTier") != null) {
            PlanTier tier = parseTier(String.valueOf(body.get("planTier")));
            org.setPlanTier(tier);
            for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(org.getId())) {
                if (m.getUser().getRole() == Role.RECRUTEUR) {
                    m.getUser().setPlanTier(tier);
                }
            }
        }
        organizationRepository.save(org);
        return orgSummary(org);
    }

    @GetMapping("/invoices")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> invoices(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BillingInvoice inv : billingInvoiceRepository.findAllByOrderByIssuedAtDesc()) {
            Organization org = inv.getOrganization();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("number", inv.getNumber());
            row.put("issuedAt", inv.getIssuedAt() == null ? "" : inv.getIssuedAt().toString());
            row.put("organization", org == null ? "" : org.getName());
            row.put("ice", org == null || org.getIce() == null ? "" : org.getIce());
            row.put("plan", inv.getPlanCode());
            row.put("planLabel", inv.getPlanLabel());
            row.put("cycle", inv.getCycle());
            row.put("amountHt", inv.getAmountHt());
            row.put("tva", inv.getTva());
            row.put("amountTtc", inv.getAmountTtc());
            row.put("status", inv.getStatus());
            row.put("currency", "MAD");
            rows.add(row);
        }
        return rows;
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> plans(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        return List.of(
                plan("ACCES", "Accès recruteur", 0, "Publication et suivi, sans mise en avant"),
                plan("PREMIUM", "Premium recruteur", 299, "Équipe, offres mises en avant, alertes de délai"),
                plan("ESSENTIEL", "Essentiel candidat", 0, "Candidatures et matching"),
                plan("PERFORMANCE", "Performance candidat", 39, "Alertes et préparation d’entretien")
        );
    }

    @PostMapping("/invoices")
    @Transactional(readOnly = true)
    public Map<String, Object> invoice(
            @AuthenticationPrincipal User admin,
            @RequestBody Map<String, Object> body
    ) {
        requireAdmin(admin);
        Long orgId = body.get("organizationId") == null ? null : Long.valueOf(String.valueOf(body.get("organizationId")));
        Organization org = organizationRepository.findById(orgId == null ? -1 : orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
        String cycle = "year".equals(String.valueOf(body.get("cycle"))) ? "year" : "month";
        PlanTier tier = body.get("planTier") == null ? org.getPlanTier() : parseTier(String.valueOf(body.get("planTier")));
        int monthly = monthlyPrice(tier);
        int ttc = "year".equals(cycle) ? monthly * 10 : monthly;
        double ht = Math.round((ttc / 1.2) * 100.0) / 100.0;
        double tva = Math.round((ttc - ht) * 100.0) / 100.0;
        String number = "HL-" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Africa/Casablanca")).format(Instant.now())
                + "-" + org.getId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", number);
        out.put("issuedAt", Instant.now().toString());
        out.put("organization", org.getName());
        out.put("ice", org.getIce() == null ? "" : org.getIce());
        out.put("plan", tier.name());
        out.put("planLabel", planLabel(tier));
        out.put("cycle", cycle);
        out.put("amountHt", ht);
        out.put("tva", tva);
        out.put("amountTtc", ttc);
        out.put("currency", "MAD");
        return out;
    }

    private Map<String, Object> orgSummary(Organization org) {
        List<Membership> members = membershipRepository.findByOrganizationIdAndActiveTrue(org.getId());
        int offers = 0;
        for (Membership m : members) {
            offers += jobOfferRepository.findByRecruiterId(m.getUser().getId()).size();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", org.getId());
        row.put("name", org.getName());
        row.put("ice", org.getIce() == null ? "" : org.getIce());
        row.put("verified", org.isVerified());
        row.put("verificationStatus", org.getVerificationStatus() == null ? "" : org.getVerificationStatus());
        row.put("verificationMethod", org.getVerificationMethod() == null ? "" : org.getVerificationMethod());
        row.put("statusLabel", companyVerificationService.statusLabel(org.getVerificationStatus(), org.isVerified()));
        row.put("logoUrl", org.getLogoUrl() == null ? "" : org.getLogoUrl());
        row.put("website", org.getWebsite() == null ? "" : org.getWebsite());
        row.put("sector", org.getSector() == null ? "" : org.getSector());
        row.put("city", org.getCity() == null ? "" : org.getCity());
        row.put("planTier", org.getPlanTier().name());
        row.put("ownerName", org.getOwner() == null ? "" : org.getOwner().getFullName());
        row.put("ownerEmail", org.getOwner() == null ? "" : org.getOwner().getEmail());
        row.put("memberCount", members.size());
        row.put("offerCount", offers);
        row.put("createdAt", org.getCreatedAt() == null ? "" : org.getCreatedAt().toString());
        return row;
    }

    private Map<String, Object> plan(String code, String label, int monthly, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("label", label);
        row.put("monthlyMad", monthly);
        row.put("yearlyMad", monthly * 10);
        row.put("detail", detail);
        return row;
    }

    private int monthlyPrice(PlanTier tier) {
        return switch (tier) {
            case PREMIUM -> 299;
            case PERFORMANCE -> 39;
            default -> 0;
        };
    }

    private String planLabel(PlanTier tier) {
        return switch (tier) {
            case PREMIUM -> "Premium recruteur";
            case PERFORMANCE -> "Performance candidat";
            case ESSENTIEL -> "Essentiel candidat";
            case ACCES -> "Accès recruteur";
        };
    }

    private PlanTier parseTier(String raw) {
        try {
            return PlanTier.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan inconnu");
        }
    }

    private void requireAdmin(User user) {
        if (user == null || user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin uniquement");
        }
    }
}

