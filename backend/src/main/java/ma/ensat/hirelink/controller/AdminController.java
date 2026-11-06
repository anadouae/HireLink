package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.ApplicationStatus;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.OfferReport;
import ma.ensat.hirelink.repository.AuditLogRepository;
import ma.ensat.hirelink.repository.AiUsageLogRepository;
import ma.ensat.hirelink.repository.OfferReportRepository;
import ma.ensat.hirelink.service.AuditService;
import ma.ensat.hirelink.service.AiCreditService;
import ma.ensat.hirelink.service.JobOfferService;
import ma.ensat.hirelink.service.SessionService;
import ma.ensat.hirelink.service.BillingCatalogService;
import ma.ensat.hirelink.service.CompanyVerificationService;
import ma.ensat.hirelink.service.EventNotifier;
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
import org.springframework.http.ResponseEntity;

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
    private final EventNotifier eventNotifier;
    private final OrganizationService organizationService;
    private final AuditService audit;
    private final SessionService sessionService;
    private final AuditLogRepository auditLogRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiCreditService aiCreditService;
    private final OfferReportRepository offerReportRepository;
    private final JobOfferService jobOfferService;

    public AdminController(
            UserRepository userRepository,
            JobOfferRepository jobOfferRepository,
            ApplicationRepository applicationRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            BillingCatalogService billingCatalogService,
            CompanyVerificationService companyVerificationService,
            BillingInvoiceRepository billingInvoiceRepository,
            EventNotifier eventNotifier,
            OrganizationService organizationService,
            AuditService audit,
            SessionService sessionService,
            AuditLogRepository auditLogRepository,
            AiUsageLogRepository aiUsageLogRepository,
            AiCreditService aiCreditService,
            OfferReportRepository offerReportRepository,
            JobOfferService jobOfferService
    ) {
        this.audit = audit;
        this.sessionService = sessionService;
        this.auditLogRepository = auditLogRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.aiCreditService = aiCreditService;
        this.offerReportRepository = offerReportRepository;
        this.jobOfferService = jobOfferService;
        this.organizationService = organizationService;
        this.eventNotifier = eventNotifier;
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
        Map<Long, ma.ensat.hirelink.model.Membership> memberOf = new java.util.HashMap<>();
        for (ma.ensat.hirelink.model.Membership m : membershipRepository.findAllActiveWithOrganization()) {
            memberOf.merge(m.getUser().getId(), m, (a, b) -> a.getOrgRole().ordinal() <= b.getOrgRole().ordinal() ? a : b);
        }
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
            ma.ensat.hirelink.model.Membership m = memberOf.get(u.getId());
            row.put("orgRole", m == null ? null : m.getOrgRole().name());
            row.put("orgId", m == null ? null : m.getOrganization().getId());
            row.put("orgName", m == null ? null : m.getOrganization().getName());
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
                eventNotifier.organizationDecision(org, verified, m.getUser().getId());
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

    /** Encaissement constaté par l'administrateur : EMISE (à encaisser) ou REGLEE (payée). */
    @PatchMapping("/invoices/{id}")
    public Map<String, Object> updateInvoice(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        requireAdmin(admin);
        BillingInvoice inv = billingCatalogService.markStatus(id, body.get("status"), body.get("note"));
        audit.ok("INVOICE_STATUS_CHANGED", admin, "INVOICE", inv.getId(), inv.getNumber() + " → " + inv.getStatus());
        return billingCatalogService.toInvoice(inv);
    }

    /** Un avoir annule une facture sans la réécrire : les deux lignes restent consultables. */
    @PostMapping("/invoices/{id}/credit-note")
    public Map<String, Object> creditNote(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        requireAdmin(admin);
        BillingInvoice avoir = billingCatalogService.creditNote(id, body.get("motif"));
        audit.ok("INVOICE_CREDIT_NOTE", admin, "INVOICE", avoir.getId(), avoir.getNumber() + " annule " + avoir.getCancelsInvoiceId());
        return billingCatalogService.toInvoice(avoir);
    }

    /* ---------------------------------------------------------------- modération */

    /**
     * File de modération : une ligne par offre signalée, avec ses alertes non tranchées.
     * Le sujet à décider est l'offre — trois signalements sur la même annonce font une décision, pas trois.
     */
    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public Map<String, Object> reports(@AuthenticationPrincipal User admin) {
        requireAdmin(admin);
        List<OfferReport> pending = offerReportRepository.findByStatusOrderByIdDesc("PENDING");
        Map<Long, User> reporters = new LinkedHashMap<>();
        userRepository.findAllById(pending.stream().map(OfferReport::getReporterUserId).toList()).forEach(u -> reporters.put(u.getId(), u));
        Map<Long, JobOffer> offers = new LinkedHashMap<>();
        jobOfferRepository.findAllById(pending.stream().map(OfferReport::getJobOfferId).toList()).forEach(o -> offers.put(o.getId(), o));
        Map<Long, Integer> apps = applicationsByOffer();
        Map<Long, String> orgNames = orgNamesByRecruiter();

        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (OfferReport r : pending) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("reason", r.getReason());
            item.put("at", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
            User rep = reporters.get(r.getReporterUserId());
            item.put("reporter", rep == null ? "compte supprimé" : rep.getFullName() + " · " + rep.getEmail());
            grouped.computeIfAbsent(r.getJobOfferId(), k -> new ArrayList<>()).add(item);
        }

        List<Map<String, Object>> queue = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> e : grouped.entrySet()) {
            JobOffer o = offers.get(e.getKey());
            if (o == null) continue;
            Map<String, Object> row = offerRow(o, apps.getOrDefault(o.getId(), 0), orgNames);
            row.put("reports", e.getValue());
            queue.add(row);
        }
        queue.sort((a, b) -> Integer.compare(((List<?>) b.get("reports")).size(), ((List<?>) a.get("reports")).size()));

        List<Map<String, Object>> handled = new ArrayList<>();
        List<OfferReport> done = offerReportRepository.findByStatusInOrderByResolvedAtDesc(List.of("FONDE", "ECARTE"));
        Map<Long, JobOffer> doneOffers = new LinkedHashMap<>();
        jobOfferRepository.findAllById(done.stream().map(OfferReport::getJobOfferId).toList()).forEach(o -> doneOffers.put(o.getId(), o));
        for (OfferReport r : done) {
            if (handled.size() >= 30) break;
            JobOffer o = doneOffers.get(r.getJobOfferId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("offerId", r.getJobOfferId());
            row.put("offerTitle", o == null ? "offre supprimée" : o.getTitle());
            row.put("reason", r.getReason());
            row.put("decision", r.getStatus());
            row.put("resolvedAt", r.getResolvedAt() == null ? "" : r.getResolvedAt().toString());
            row.put("handledBy", r.getHandledByEmail() == null ? "" : r.getHandledByEmail());
            handled.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("queue", queue);
        out.put("handled", handled);
        out.put("pendingCount", pending.size());
        out.put("suspendedOffers", jobOfferRepository.findAll().stream().filter(JobOffer::isSuspended).count());
        return out;
    }

    /** Toutes les offres de la plateforme, avec ce qui décide d'une sanction : état, signalements, dossiers. */
    @GetMapping("/offers")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> offers(@AuthenticationPrincipal User admin) {
        requireAdmin(admin);
        Map<Long, Integer> apps = applicationsByOffer();
        Map<Long, String> orgNames = orgNamesByRecruiter();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JobOffer o : jobOfferRepository.findAll()) {
            rows.add(offerRow(o, apps.getOrDefault(o.getId(), 0), orgNames));
        }
        rows.sort((a, b) -> Long.compare(((Number) b.get("id")).longValue(), ((Number) a.get("id")).longValue()));
        return rows;
    }

    @PostMapping("/offers/{id}/suspend")
    public ApiDtos.JobOfferResponse suspendOffer(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        requireAdmin(admin);
        ApiDtos.JobOfferResponse dto = jobOfferService.adminSuspend(admin, id, body == null ? null : body.get("reason"));
        audit.ok("OFFER_SUSPENDED", admin, "OFFER", id, dto.title() + " · " + (body == null ? "" : body.get("reason")));
        return dto;
    }

    @PostMapping("/offers/{id}/restore")
    public ApiDtos.JobOfferResponse restoreOffer(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        requireAdmin(admin);
        ApiDtos.JobOfferResponse dto = jobOfferService.adminRestore(admin, id);
        audit.ok("OFFER_RESTORED", admin, "OFFER", id, dto.title());
        return dto;
    }

    @PostMapping("/offers/{id}/unpublish")
    public ApiDtos.JobOfferResponse unpublishOffer(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        requireAdmin(admin);
        ApiDtos.JobOfferResponse dto = jobOfferService.adminUnpublish(admin, id);
        audit.ok("OFFER_UNPUBLISHED", admin, "OFFER", id, dto.title());
        return dto;
    }

    /** Écarter = « hors sujet » : les signalements sont clos, l'offre n'est pas touchée. */
    @PostMapping("/offers/{id}/dismiss-reports")
    public Map<String, Object> dismissReports(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        requireAdmin(admin);
        int handled = jobOfferService.adminDismissReports(admin, id);
        audit.ok("OFFER_REPORTS_DISMISSED", admin, "OFFER", id, handled + " signalement(s) écarté(s)");
        return Map.of("handled", handled);
    }

    /* ------------------------------------------------------------------ recherche */

    /** Une seule question, quatre réponses : où chercher quand on ne sait pas quelle entité on a sous les yeux. */
    @GetMapping("/search")
    @Transactional(readOnly = true)
    public Map<String, Object> search(@AuthenticationPrincipal User admin, @RequestParam(required = false) String q) {
        requireAdmin(admin);
        String term = q == null ? "" : q.trim();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("term", term);
        if (term.length() < 2) {
            out.put("users", List.of());
            out.put("organizations", List.of());
            out.put("offers", List.of());
            out.put("applications", List.of());
            return out;
        }
        String like = "%" + term.toLowerCase() + "%";
        Map<Long, String> orgNames = orgNamesByRecruiter();

        List<Map<String, Object>> users = new ArrayList<>();
        for (User u : userRepository.findTop10ByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("fullName", u.getFullName());
            row.put("email", u.getEmail());
            row.put("role", u.getRole().name());
            row.put("active", u.isActive());
            users.add(row);
        }

        List<Map<String, Object>> orgs = new ArrayList<>();
        for (Organization org : organizationRepository.findTop10ByNameContainingIgnoreCaseOrIceContainingIgnoreCase(term, term)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", org.getId());
            row.put("name", org.getName());
            row.put("ice", org.getIce() == null ? "" : org.getIce());
            row.put("city", org.getCity() == null ? "" : org.getCity());
            row.put("verified", org.isVerified());
            orgs.add(row);
        }

        Map<Long, Integer> apps = applicationsByOffer();
        List<Map<String, Object>> foundOffers = new ArrayList<>();
        for (JobOffer o : jobOfferRepository.findTop10ByTitleContainingIgnoreCaseOrLocationContainingIgnoreCase(term, term)) {
            foundOffers.add(offerRow(o, apps.getOrDefault(o.getId(), 0), orgNames));
        }

        List<Map<String, Object>> foundApps = new ArrayList<>();
        for (ma.ensat.hirelink.model.Application a : applicationRepository.search(like, org.springframework.data.domain.PageRequest.of(0, 10))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("candidate", a.getCandidate().getFullName());
            row.put("candidateEmail", a.getCandidate().getEmail());
            row.put("offerId", a.getJobOffer().getId());
            row.put("offerTitle", a.getJobOffer().getTitle());
            row.put("status", a.getStatus().name());
            row.put("submittedAt", a.getSubmittedAt() == null ? "" : a.getSubmittedAt().toString());
            foundApps.add(row);
        }

        out.put("users", users);
        out.put("organizations", orgs);
        out.put("offers", foundOffers);
        out.put("applications", foundApps);
        return out;
    }

    /* --------------------------------------------------------------------- export */

    /** Export CSV (séparateur point-virgule, BOM UTF-8) : la copie d'écran du journal ne suffit pas à un auditeur. */
    @GetMapping(value = "/audit/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportAudit(@AuthenticationPrincipal User admin, @RequestParam(required = false) String action) {
        requireAdmin(admin);
        String filter = action == null || action.isBlank() ? "" : action.trim();
        List<ma.ensat.hirelink.model.AuditLog> rows = filter.isEmpty()
                ? auditLogRepository.findTop2000ByOrderByIdDesc()
                : auditLogRepository.findTop2000ByActionOrderByIdDesc(filter);
        StringBuilder csv = new StringBuilder("Date;Action;Compte;Cible;IP;Résultat;Détail\n");
        for (var a : rows) {
            csv.append(csvCell(a.getCreatedAt() == null ? "" : a.getCreatedAt().toString())).append(';')
                    .append(csvCell(a.getAction())).append(';')
                    .append(csvCell(a.getActorEmail() == null ? "" : a.getActorEmail())).append(';')
                    .append(csvCell(a.getTargetType() == null ? "" : a.getTargetType() + ":" + a.getTargetId())).append(';')
                    .append(csvCell(a.getIp() == null ? "" : a.getIp())).append(';')
                    .append(csvCell(a.isSuccess() ? "ok" : "échec")).append(';')
                    .append(csvCell(a.getDetail() == null ? "" : a.getDetail())).append('\n');
        }
        audit.ok("AUDIT_EXPORTED", admin, "AUDIT_LOG", null, rows.size() + " ligne(s)" + (filter.isEmpty() ? "" : " · action=" + filter));
        String name = "audit-hirelink-" + java.time.LocalDate.now() + (filter.isEmpty() ? "" : "-" + filter.toLowerCase()) + ".csv";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new org.springframework.http.MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body("\uFEFF" + csv);
    }

    private static String csvCell(String raw) {
        String v = raw == null ? "" : raw.replace("\r", " ").replace("\n", " ");
        if (v.contains(";") || v.contains("\"")) return '"' + v.replace("\"", "\"\"") + '"';
        return v;
    }

    private Map<Long, Integer> applicationsByOffer() {
        Map<Long, Integer> apps = new LinkedHashMap<>();
        for (Object[] row : applicationRepository.countGroupedByOffer()) {
            if (row[0] != null) apps.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return apps;
    }

    /** Le nom de l'entreprise vient de l'équipe active ; à défaut, de ce que le recruteur a saisi lui-même. */
    private Map<Long, String> orgNamesByRecruiter() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Membership m : membershipRepository.findAllActiveWithOrganization()) {
            names.putIfAbsent(m.getUser().getId(), m.getOrganization().getName());
        }
        return names;
    }

    private Map<String, Object> offerRow(JobOffer o, int applications, Map<Long, String> orgNames) {
        User rec = o.getRecruiter();
        String org = rec == null ? "" : orgNames.getOrDefault(rec.getId(), rec.getCompany() == null ? "" : rec.getCompany());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", o.getId());
        row.put("title", o.getTitle());
        row.put("recruiter", rec == null ? "" : rec.getFullName());
        row.put("recruiterEmail", rec == null ? "" : rec.getEmail());
        row.put("organization", org);
        row.put("location", o.getLocation() == null ? "" : o.getLocation());
        row.put("contractType", o.getContractType() == null ? "" : o.getContractType());
        row.put("published", o.isPublished());
        row.put("suspended", o.isSuspended());
        row.put("closedAt", o.getClosedAt() == null ? "" : o.getClosedAt().toString());
        row.put("archivedAt", o.getArchivedAt() == null ? "" : o.getArchivedAt().toString());
        row.put("quarantineReason", o.getQuarantineReason() == null ? "" : o.getQuarantineReason());
        row.put("reportCount", o.getReportCount());
        row.put("applications", applications);
        row.put("deadline", o.getDeadline() == null ? "" : o.getDeadline().toString());
        row.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString());
        return row;
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

    /**
     * Consommation IA du mois en cours, par entreprise et par compte individuel.
     * Le plafond est une somme de sièges : ce qui compte est le taux d'occupation de l'enveloppe.
     */
    @GetMapping("/ai-usage")
    @Transactional(readOnly = true)
    public Map<String, Object> aiUsage(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        java.time.YearMonth ym = java.time.YearMonth.now(java.time.ZoneOffset.UTC);
        java.time.Instant from = ym.atDay(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

        // un compte = un total de crédits, identique à celui qu'il voit lui-même ; on le rattache
        // à l'organisation inscrite dans ses journaux, sinon à son organisation actuelle
        Map<Long, int[]> useByUser = new LinkedHashMap<>();
        Map<Long, Long> orgFromLogs = new LinkedHashMap<>();
        for (Object[] row : aiUsageLogRepository.spentByUserSince(from)) {
            Long userId = row[1] == null ? null : ((Number) row[1]).longValue();
            if (userId == null) continue;
            int[] acc = useByUser.computeIfAbsent(userId, k -> new int[2]);
            acc[0] += row[2] == null ? 0 : ((Number) row[2]).intValue();
            acc[1] += row[3] == null ? 0 : ((Number) row[3]).intValue();
            if (row[0] != null) orgFromLogs.putIfAbsent(userId, ((Number) row[0]).longValue());
        }

        Map<Long, Integer> seatsByOrg = new LinkedHashMap<>();
        for (Membership m : membershipRepository.findAllActiveWithOrganization()) {
            seatsByOrg.merge(m.getOrganization().getId(), 1, Integer::sum);
        }

        Map<Long, User> personById = new LinkedHashMap<>();
        for (User p : userRepository.findAllById(useByUser.keySet())) personById.put(p.getId(), p);

        Map<Long, Map<Long, int[]>> byOrg = new LinkedHashMap<>();
        for (Map.Entry<Long, int[]> e : useByUser.entrySet()) {
            User p = personById.get(e.getKey());
            Long orgId = orgFromLogs.get(e.getKey());
            if (orgId == null && p != null) orgId = p.getAssignedOrgId();
            byOrg.computeIfAbsent(orgId == null ? 0L : orgId, k -> new LinkedHashMap<>()).put(e.getKey(), e.getValue());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int quietOrgs = 0;
        int quietEnvelope = 0;
        for (Organization org : organizationRepository.findAll()) {
            Map<Long, int[]> use = byOrg.getOrDefault(org.getId(), Map.of());
            int seats = Math.max(1, seatsByOrg.getOrDefault(org.getId(), use.size()));
            int limit = billingCatalogService.require(org.getPlanTier()).getCredits() * seats;
            int credits = use.values().stream().mapToInt(v -> v[1]).sum();
            if (credits == 0) {
                quietOrgs++;
                quietEnvelope += limit;
                continue;
            }
            rows.add(usageRow(org.getName(), org.getPlanTier(), seats, use, personById));
        }
        // les comptes sans entreprise rattachée (candidats) portent leur propre formule
        for (Map.Entry<Long, int[]> e : byOrg.getOrDefault(0L, Map.of()).entrySet()) {
            User p = personById.get(e.getKey());
            if (p == null) continue;
            rows.add(usageRow(p.getFullName(), aiCreditService.resolveTier(p), 1, Map.of(e.getKey(), e.getValue()), personById));
        }
        rows.sort((a, b) -> Integer.compare(((Number) b.get("credits")).intValue(), ((Number) a.get("credits")).intValue()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("quietOrganizations", quietOrgs);
        out.put("quietEnvelope", quietEnvelope);
        return out;
    }

    private Map<String, Object> usageRow(
            String name,
            PlanTier tier,
            int seats,
            Map<Long, int[]> use,
            Map<Long, User> personById
    ) {
        ma.ensat.hirelink.model.CommercialPlan plan = billingCatalogService.require(tier);
        int actions = 0;
        int credits = 0;
        List<Map<String, Object>> people = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : use.entrySet()) {
            actions += e.getValue()[0];
            credits += e.getValue()[1];
            User p = personById.get(e.getKey());
            Map<String, Object> person = new LinkedHashMap<>();
            person.put("fullName", p == null ? "#" + e.getKey() : p.getFullName());
            person.put("email", p == null ? "" : p.getEmail());
            person.put("actions", e.getValue()[0]);
            person.put("credits", e.getValue()[1]);
            people.add(person);
        }
        people.sort((a, b) -> Integer.compare(((Number) b.get("credits")).intValue(), ((Number) a.get("credits")).intValue()));

        int limit = plan.getCredits() * Math.max(1, seats);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("organization", name);
        row.put("planTier", tier.name());
        row.put("planLabel", plan.getLabel());
        row.put("monthlyMad", plan.getMonthlyMad());
        row.put("seats", seats);
        row.put("limit", limit);
        row.put("limitPerSeat", plan.getCredits());
        row.put("actions", actions);
        row.put("credits", credits);
        row.put("remaining", Math.max(0, limit - credits));
        row.put("people", people);
        return row;
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

