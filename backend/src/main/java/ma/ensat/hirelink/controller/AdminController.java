package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.repository.AuditLogRepository;
import ma.ensat.hirelink.service.AuditService;
import ma.ensat.hirelink.service.SessionService;
import ma.ensat.hirelink.service.BillingCatalogService;
import ma.ensat.hirelink.service.CompanyVerificationService;
import ma.ensat.hirelink.service.NotificationService;
import ma.ensat.hirelink.service.OrganizationService;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMINISTRATEUR')")
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
    private final NotificationService notificationService;
    private final OrganizationService organizationService;
    private final AuditService audit;
    private final SessionService sessionService;
    private final AuditLogRepository auditLogRepository;

    public AdminController(
            UserRepository userRepository,
            JobOfferRepository jobOfferRepository,
            ApplicationRepository applicationRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            BillingCatalogService billingCatalogService,
            CompanyVerificationService companyVerificationService,
            BillingInvoiceRepository billingInvoiceRepository,
            NotificationService notificationService,
            OrganizationService organizationService,
            AuditService audit,
            SessionService sessionService,
            AuditLogRepository auditLogRepository
    ) {
        this.audit = audit;
        this.sessionService = sessionService;
        this.auditLogRepository = auditLogRepository;
        this.organizationService = organizationService;
        this.notificationService = notificationService;
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
        boolean active = Boolean.TRUE.equals(body.get("active"));
        if (!active && u.getId().equals(admin.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas désactiver votre propre compte");
        }
        u.setActive(active);
        userRepository.save(u);
        if (!active) {
            sessionService.revokeAll(u.getId(), "ACCOUNT_SUSPENDED", null);
        }
        audit.ok(active ? "USER_REACTIVATED" : "USER_SUSPENDED", admin, "USER", u.getId(), u.getEmail());
        return Map.of("id", u.getId(), "active", u.isActive());
    }

    /** Journal d'audit : les 200 derniers évènements de sécurité (filtre optionnel par action). */
    @GetMapping("/audit")
    public List<Map<String, Object>> auditLog(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String action
    ) {
        requireAdmin(user);
        var page = org.springframework.data.domain.PageRequest.of(0, 200);
        var rows = action == null || action.isBlank()
                ? auditLogRepository.findAllByOrderByIdDesc(page)
                : auditLogRepository.findByActionOrderByIdDesc(action.trim(), page);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var a : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("at", a.getCreatedAt().toString());
            m.put("actor", a.getActorEmail());
            m.put("action", a.getAction());
            m.put("target", a.getTargetType() == null ? null : a.getTargetType() + ":" + a.getTargetId());
            m.put("ip", a.getIp());
            m.put("success", a.isSuccess());
            m.put("detail", a.getDetail());
            out.add(m);
        }
        return out;
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
            audit.ok(verified ? "ORG_VERIFIED" : "ORG_VERIFICATION_REMOVED", admin, "ORGANIZATION", org.getId(), org.getName() + " · ICE " + org.getIce());
            for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(org.getId())) {
                if (m.getUser().getRole() == Role.RECRUTEUR) {
                    m.getUser().setCompanyVerified(verified);
                    m.getUser().setVerificationStatus(org.getVerificationStatus());
                    m.getUser().setCompany(org.getName());
                    m.getUser().setCompanyIce(org.getIce());
                }
            }
            for (Membership m : membershipRepository.findByOrganizationIdAndActiveTrue(org.getId())) {
                notificationService.push(m.getUser().getId(),
                        verified ? "Entreprise vérifiée" : "Vérification retirée",
                        verified
                                ? "« " + org.getName() + " » est vérifiée : vous pouvez publier des offres."
                                : "La vérification de « " + org.getName() + " » a été retirée par l'administration.",
                        "SYSTEM");
            }
        }
        if (body.get("planTier") != null) {
            PlanTier tier = parseTier(String.valueOf(body.get("planTier")));
            if (tier != org.getPlanTier()) {
                organizationService.changePlan(org, tier, String.valueOf(body.get("cycle")));
                audit.ok("ORG_PLAN_CHANGED", admin, "ORGANIZATION", org.getId(), org.getName() + " → " + tier);
            }
        }
        organizationRepository.save(org);
        return orgSummary(org);
    }

    @GetMapping("/invoices")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> invoices(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        return billingCatalogService.invoices().stream().map(billingCatalogService::toInvoice).toList();
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> plans(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        return billingCatalogService.plans().stream().map(billingCatalogService::toPlan).toList();
    }

    @PatchMapping("/plans/{code}")
    public Map<String, Object> updatePlan(
            @AuthenticationPrincipal User admin,
            @PathVariable String code,
            @RequestBody Map<String, Object> body
    ) {
        requireAdmin(admin);
        return billingCatalogService.toPlan(billingCatalogService.update(code, body));
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

