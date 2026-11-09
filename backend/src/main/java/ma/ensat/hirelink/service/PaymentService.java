package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.OrgRole;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PaymentTransaction;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.BillingInvoiceRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.PaymentTransactionRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Abonnement et paiement simulé. Le montant vient toujours de la facture, jamais du navigateur. */
@Service
public class PaymentService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
    private static final Set<String> CARD_KEYS = Set.of("pan", "card", "cardnumber", "number", "cvc", "cvv", "cryptogramme");

    private final BillingCatalogService billing;
    private final BillingInvoiceRepository invoices;
    private final PaymentTransactionRepository transactions;
    private final PaymentProvider provider;
    private final OrganizationService organizations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AuditService audit;
    private final InvoicePdfService pdf;

    public PaymentService(BillingCatalogService billing, BillingInvoiceRepository invoices,
                          PaymentTransactionRepository transactions, PaymentProvider provider,
                          OrganizationService organizations, MembershipRepository memberships,
                          UserRepository users, AuditService audit, InvoicePdfService pdf) {
        this.billing = billing;
        this.invoices = invoices;
        this.transactions = transactions;
        this.provider = provider;
        this.organizations = organizations;
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
        this.pdf = pdf;
    }

    @Transactional
    public Map<String, Object> prepare(User user, String cycle) {
        expireIfDue(user);
        user = users.findById(user.getId()).orElseThrow();
        String period = "year".equalsIgnoreCase(cycle) ? "year" : "month";
        BillingInvoice invoice;
        PlanTier current;
        if (user.getRole() == Role.CANDIDAT) {
            current = user.getPlanTier() == null ? PlanTier.ESSENTIEL : user.getPlanTier();
            invoice = billing.issuePending(null, user, PlanTier.PERFORMANCE, period);
        } else if (user.getRole() == Role.RECRUTEUR) {
            Membership m = organizations.requireMembership(user);
            if (m.getOrgRole() != OrgRole.OWNER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le créateur de l'espace règle la formule.");
            }
            current = m.getOrganization().getPlanTier() == null ? PlanTier.ACCES : m.getOrganization().getPlanTier();
            invoice = billing.issuePending(m.getOrganization(), user, PlanTier.PREMIUM, period);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non disponible pour ce rôle.");
        }
        if (invoice.getIssuedAt() != null && invoice.getIssuedAt().isAfter(Instant.now().minusSeconds(3))) {
            audit.ok("INVOICE_CREATED", user, "INVOICE", invoice.getId(), invoice.getNumber() + " · " + invoice.getAmountTtc() + " MAD");
        }
        Map<String, Object> out = state(user, current);
        out.put("pending", true);
        out.put("invoice", view(user, invoice));
        return out;
    }

    @Transactional
    public Map<String, Object> pay(User user, Map<String, Object> body) {
        rejectCardData(body);
        long invoiceId = longOf(body.get("invoiceId"));
        String scenario = String.valueOf(body.get("scenario") == null ? "" : body.get("scenario")).trim().toUpperCase();
        String key = String.valueOf(body.get("idempotencyKey") == null ? "" : body.get("idempotencyKey")).trim();
        if (!key.matches("[A-Za-z0-9_-]{8,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clé d'idempotence invalide.");
        }
        BillingInvoice invoice = invoices.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facture introuvable"));
        var existing = transactions.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().getInvoice().getId().equals(invoice.getId()) || !canView(user, invoice)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette clé appartient à une autre facture.");
            }
            return transactionView(existing.get());
        }
        if (!debtorOf(user, invoice)) {
            audit.fail("PAYMENT_DENIED", user.getEmail(), "Facture " + invoice.getNumber());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'êtes pas le débiteur de cette facture.");
        }
        if (body.get("amountTtc") != null && Math.abs(doubleOf(body.get("amountTtc")) - invoice.getAmountTtc()) > 0.001) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant est fixé par la facture.");
        }
        if ("ANNULEE".equals(invoice.canonicalStatus()) || "AVOIR".equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une facture annulée ne peut pas être payée.");
        }
        if ("PAYEE".equals(invoice.canonicalStatus()) || transactions.existsByInvoiceIdAndStatus(invoice.getId(), PaymentTransaction.Status.SUCCEEDED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette facture est déjà payée.");
        }
        PaymentProvider.Outcome outcome = provider.charge(invoice, scenario);
        PaymentTransaction tx = new PaymentTransaction();
        tx.setInvoice(invoice);
        tx.setActorUserId(user.getId());
        tx.setIdempotencyKey(key);
        tx.setProvider(provider.name());
        tx.setReference(outcome.reference());
        tx.setAmountTtc(invoice.getAmountTtc());
        tx.setCurrency("MAD");
        tx.setScenario(scenario);
        tx.setCompletedAt(Instant.now());
        if (!outcome.success()) {
            tx.setStatus(PaymentTransaction.Status.FAILED);
            tx.setFailureReason(outcome.reason());
            transactions.save(tx);
            audit.log("PAYMENT_FAILED", user, "INVOICE", invoice.getId(), false, invoice.getNumber() + " · " + outcome.reference());
            return transactionView(tx);
        }
        tx.setStatus(PaymentTransaction.Status.SUCCEEDED);
        transactions.save(tx);
        invoice.setStatus("PAYEE");
        invoice.setPaidAt(tx.getCompletedAt());
        invoice.setTransactionRef(tx.getReference());
        Instant end = periodEnd(invoice.getCycle(), currentEnd(user, invoice));
        invoice.setPeriodEnd(end);
        if (Boolean.FALSE.equals(invoice.getRightsApplied())) {
            grant(user, invoice, end);
            invoice.setRightsApplied(true);
        }
        invoices.save(invoice);
        audit.ok("PAYMENT_SUCCEEDED", user, "INVOICE", invoice.getId(), invoice.getNumber() + " · " + tx.getReference());
        Map<String, Object> out = transactionView(tx);
        out.put("planTier", freshTier(user));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> payment(User user, Long id) {
        PaymentTransaction tx = transactions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
        if (!canView(user, tx.getInvoice())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction inaccessible.");
        }
        return transactionView(tx);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> paymentByKey(User user, String key) {
        PaymentTransaction tx = transactions.findByIdempotencyKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
        if (!canView(user, tx.getInvoice())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction inaccessible.");
        }
        return transactionView(tx);
    }

    @Transactional
    public Map<String, Object> cancel(User user) {
        expireIfDue(user);
        user = users.findById(user.getId()).orElseThrow();
        if (user.getRole() == Role.CANDIDAT) {
            if (user.getPlanTier() != PlanTier.PERFORMANCE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun abonnement payant à résilier.");
            }
            user.setCancelAtPeriodEnd(true);
            users.save(user);
            audit.ok("SUBSCRIPTION_CANCEL_REQUESTED", user, "USER", user.getId(), untilText(user.getSubscriptionEndsAt()));
            return cancelView(user.getPlanTier().name(), user.getSubscriptionEndsAt());
        }
        Membership m = organizations.requireMembership(user);
        if (m.getOrgRole() != OrgRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le créateur de l'espace résilie la formule.");
        }
        Organization org = m.getOrganization();
        if (org.getPlanTier() != PlanTier.PREMIUM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun abonnement payant à résilier.");
        }
        org.setCancelAtPeriodEnd(true);
        audit.ok("SUBSCRIPTION_CANCEL_REQUESTED", user, "ORGANIZATION", org.getId(), untilText(org.getSubscriptionEndsAt()));
        return cancelView(org.getPlanTier().name(), org.getSubscriptionEndsAt());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(User user, String scope) {
        List<BillingInvoice> rows;
        if ("company".equals(scope)) {
            if (user.getRole() != Role.RECRUTEUR) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé à l'entreprise.");
            }
            Membership m = organizations.requireMembership(user);
            rows = invoices.findByOrganizationIdOrderByIssuedAtDesc(m.getOrganization().getId());
        } else {
            rows = invoices.findByDebtorUserIdOrderByIssuedAtDesc(user.getId());
        }
        return rows.stream().filter(row -> canView(user, row)).map(row -> view(user, row)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> one(User user, Long id) {
        BillingInvoice invoice = invoices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facture introuvable"));
        if (!canView(user, invoice)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Facture inaccessible.");
        }
        return view(user, invoice);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(User user, Long id) {
        BillingInvoice invoice = invoices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facture introuvable"));
        if (user.getRole() != Role.ADMINISTRATEUR && !canView(user, invoice)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Facture inaccessible.");
        }
        return pdf.render(invoice);
    }

    public Map<String, Object> view(User viewer, BillingInvoice invoice) {
        Map<String, Object> row = billing.toInvoice(invoice);
        row.put("canPay", viewer != null && viewer.getRole() != Role.ADMINISTRATEUR && canPay(viewer, invoice));
        return row;
    }

    private void grant(User actor, BillingInvoice invoice, Instant end) {
        PlanTier tier = PlanTier.valueOf(invoice.getPlanCode());
        if (invoice.getOrganization() != null) {
            organizations.applyPaidPlan(invoice.getOrganization().getId(), tier, end);
        } else {
            User debtor = users.findByIdForUpdate(invoice.getDebtorUser().getId()).orElseThrow();
            debtor.setPlanTier(tier);
            debtor.setSubscriptionEndsAt(end);
            debtor.setCancelAtPeriodEnd(false);
            users.save(debtor);
        }
        audit.ok("SUBSCRIPTION_ACTIVATED", actor, "INVOICE", invoice.getId(), invoice.getPlanCode() + " jusqu'au " + end);
    }

    private void expireIfDue(User user) {
        Instant now = Instant.now();
        if (user.getRole() == Role.CANDIDAT && user.isCancelAtPeriodEnd()
                && user.getSubscriptionEndsAt() != null && user.getSubscriptionEndsAt().isBefore(now)) {
            user.setPlanTier(PlanTier.ESSENTIEL);
            user.setSubscriptionEndsAt(null);
            user.setCancelAtPeriodEnd(false);
            users.save(user);
            audit.ok("SUBSCRIPTION_ENDED", user, "USER", user.getId(), "Retour à Essentiel");
            return;
        }
        if (user.getRole() != Role.RECRUTEUR) return;
        try {
            Membership m = organizations.requireMembership(user);
            Organization org = m.getOrganization();
            if (org.isCancelAtPeriodEnd() && org.getSubscriptionEndsAt() != null && org.getSubscriptionEndsAt().isBefore(now)) {
                organizations.downgradeExpired(org.getId());
                audit.ok("SUBSCRIPTION_ENDED", user, "ORGANIZATION", org.getId(), "Retour à Accès");
            }
        } catch (ResponseStatusException ignored) {
            // pas d'organisation : rien à clôturer
        }
    }

    private Instant currentEnd(User user, BillingInvoice invoice) {
        if (invoice.getOrganization() != null) return invoice.getOrganization().getSubscriptionEndsAt();
        return user.getSubscriptionEndsAt();
    }

    private static Instant periodEnd(String cycle, Instant existing) {
        ZonedDateTime start = ZonedDateTime.ofInstant(existing != null && existing.isAfter(Instant.now()) ? existing : Instant.now(), ZONE);
        return ("year".equals(cycle) ? start.plusYears(1) : start.plusMonths(1)).toInstant();
    }

    private String freshTier(User user) {
        User fresh = users.findById(user.getId()).orElse(user);
        if (fresh.getRole() == Role.CANDIDAT) {
            return fresh.getPlanTier() == null ? PlanTier.ESSENTIEL.name() : fresh.getPlanTier().name();
        }
        try {
            return organizations.requireMembership(fresh).getOrganization().getPlanTier().name();
        } catch (ResponseStatusException e) {
            return fresh.getPlanTier() == null ? "" : fresh.getPlanTier().name();
        }
    }

    private boolean canView(User user, BillingInvoice invoice) {
        if (user.getRole() == Role.ADMINISTRATEUR) return true;
        if (invoice.getDebtorUser() != null && invoice.getDebtorUser().getId().equals(user.getId())) return true;
        return invoice.getOrganization() != null && memberOf(user, invoice.getOrganization().getId());
    }

    private boolean canPay(User user, BillingInvoice invoice) {
        return invoice.payable() && debtorOf(user, invoice);
    }

    private boolean debtorOf(User user, BillingInvoice invoice) {
        if (user.getRole() == Role.ADMINISTRATEUR) return false;
        if (invoice.getDebtorUser() != null) return invoice.getDebtorUser().getId().equals(user.getId());
        return invoice.getOrganization() != null && ownerOf(user, invoice.getOrganization().getId());
    }

    private boolean memberOf(User user, Long orgId) {
        return memberships.findByOrganizationIdAndActiveTrue(orgId).stream().anyMatch(m -> m.getUser().getId().equals(user.getId()));
    }

    private boolean ownerOf(User user, Long orgId) {
        return memberships.findByOrganizationIdAndActiveTrue(orgId).stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()) && m.getOrgRole() == OrgRole.OWNER);
    }

    private Map<String, Object> transactionView(PaymentTransaction tx) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tx.getId());
        row.put("status", tx.getStatus().name());
        row.put("reference", tx.getReference());
        row.put("amountTtc", tx.getAmountTtc());
        row.put("currency", tx.getCurrency());
        row.put("provider", tx.getProvider());
        row.put("failureReason", tx.getFailureReason() == null ? "" : tx.getFailureReason());
        row.put("invoice", billing.toInvoice(tx.getInvoice()));
        return row;
    }

    private Map<String, Object> state(User user, PlanTier current) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planTier", current.name());
        out.put("premium", current == PlanTier.PREMIUM || current == PlanTier.PERFORMANCE);
        return out;
    }

    private Map<String, Object> cancelView(String tier, Instant until) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planTier", tier);
        out.put("premium", "PREMIUM".equals(tier) || "PERFORMANCE".equals(tier));
        out.put("cancelAtPeriodEnd", true);
        out.put("accessUntil", until == null ? "" : until.toString());
        out.put("message", until == null
                ? "Résiliation enregistrée. Aucune date de fin n'avait été fixée pour cet abonnement : l'accès déjà ouvert est conservé."
                : "Résiliation enregistrée. L'accès reste ouvert jusqu'au " + until + ".");
        return out;
    }

    private static String untilText(Instant until) {
        return until == null ? "date de fin inconnue, accès conservé" : "fin " + until;
    }

    private static void rejectCardData(Map<String, Object> body) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requête vide.");
        for (String key : body.keySet()) {
            if (CARD_KEYS.contains(key.toLowerCase().replace("_", ""))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune donnée bancaire n'est acceptée.");
            }
        }
    }

    private static long longOf(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Facture manquante.");
        }
    }

    private static double doubleOf(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(raw));
    }
}
