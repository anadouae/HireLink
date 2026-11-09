package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.model.CommercialPlan;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.repository.BillingInvoiceRepository;
import ma.ensat.hirelink.repository.CommercialPlanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BillingCatalogService {

    private final CommercialPlanRepository planRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final ma.ensat.hirelink.repository.OrganizationRepository organizationRepository;
    private final ma.ensat.hirelink.repository.UserRepository userRepository;

    @Value("${hirelink.billing.vat-rate:}")
    private String vatRate;

    public BillingCatalogService(CommercialPlanRepository planRepository,
                                 BillingInvoiceRepository invoiceRepository,
                                 ma.ensat.hirelink.repository.OrganizationRepository organizationRepository,
                                 ma.ensat.hirelink.repository.UserRepository userRepository) {
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<CommercialPlan> plans() {
        return planRepository.findAll();
    }

    @Transactional
    public CommercialPlan update(String code, Map<String, Object> body) {
        CommercialPlan plan = planRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan introuvable"));
        if (body.get("label") != null && !String.valueOf(body.get("label")).isBlank()) {
            plan.setLabel(String.valueOf(body.get("label")).trim());
        }
        if (body.get("features") != null) {
            plan.setFeatures(String.valueOf(body.get("features")).trim());
        }
        if (body.get("credits") != null) {
            plan.setCredits(Math.max(0, Integer.parseInt(String.valueOf(body.get("credits")))));
        }
        if (body.get("monthlyMad") != null) {
            plan.setMonthlyMad(Math.max(0, Integer.parseInt(String.valueOf(body.get("monthlyMad")))));
        }
        if (body.get("yearlyMad") != null) {
            plan.setYearlyMad(Math.max(0, Integer.parseInt(String.valueOf(body.get("yearlyMad")))));
        } else if (body.get("monthlyMad") != null) {
            plan.setYearlyMad(plan.getMonthlyMad() * 10);
        }
        return planRepository.save(plan);
    }

    public CommercialPlan require(PlanTier tier) {
        return planRepository.findByCode(tier.name())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan introuvable"));
    }

    private static final Set<String> OPEN = Set.of("NON_PAYEE", "EMISE");

    /**
     * Facture d'un abonnement payant, non réglée. Les droits ne sont pas accordés ici.
     * Une facture ouverte identique est réutilisée pour éviter les doublons.
     */
    @Transactional
    public BillingInvoice issuePending(Organization org, ma.ensat.hirelink.model.User debtor, PlanTier tier, String cycle) {
        CommercialPlan plan = require(tier);
        String period = "year".equalsIgnoreCase(cycle) ? "year" : "month";
        int ttc = "year".equals(period) ? plan.getYearlyMad() : plan.getMonthlyMad();
        if (ttc <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette formule est gratuite : elle ne produit pas de facture.");
        }
        BillingInvoice open = findOpen(org, debtor, plan.getCode(), period);
        if (open != null) return open;
        Amounts amounts = amounts(ttc);
        BillingInvoice invoice = new BillingInvoice();
        invoice.setNumber(org != null ? nextNumber(org.getId()) : nextUserNumber(debtor.getId()));
        invoice.setOrganization(org);
        invoice.setDebtorUser(debtor);
        invoice.setPlanCode(plan.getCode());
        invoice.setPlanLabel(plan.getLabel());
        invoice.setCycle(period);
        invoice.setAmountHt(amounts.ht());
        invoice.setTva(amounts.tva());
        invoice.setAmountTtc(ttc);
        invoice.setVatConfigured(amounts.configured());
        invoice.setStatus("NON_PAYEE");
        invoice.setRightsApplied(false);
        invoice.setIssuedAt(Instant.now());
        if (org != null) {
            invoice.setBillToName(org.getName());
            invoice.setBillToEmail(debtor.getEmail());
            invoice.setBillToExtra(org.getIce() == null || org.getIce().isBlank() ? "" : "ICE " + org.getIce());
        } else {
            invoice.setBillToName(debtor.getFullName());
            invoice.setBillToEmail(debtor.getEmail());
            invoice.setBillToExtra("");
        }
        return invoiceRepository.save(invoice);
    }

    private BillingInvoice findOpen(Organization org, ma.ensat.hirelink.model.User debtor, String plan, String period) {
        List<BillingInvoice> rows = org != null
                ? invoiceRepository.findByOrganizationIdAndStatusInOrderByIssuedAtDesc(org.getId(), OPEN)
                : invoiceRepository.findByDebtorUserIdAndOrganizationIsNullAndStatusInOrderByIssuedAtDesc(debtor.getId(), OPEN);
        for (BillingInvoice row : rows) {
            if (plan.equals(row.getPlanCode()) && period.equals(row.getCycle()) && row.payable()) return row;
        }
        return null;
    }

    private Amounts amounts(int ttc) {
        if (vatRate == null || vatRate.isBlank()) {
            return new Amounts(ttc, 0, false);
        }
        double rate;
        try {
            rate = Double.parseDouble(vatRate.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Taux de TVA illisible.");
        }
        if (rate < 0 || rate > 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Taux de TVA hors limites.");
        }
        double ht = Math.round((ttc / (1 + rate)) * 100.0) / 100.0;
        double tva = Math.round((ttc - ht) * 100.0) / 100.0;
        return new Amounts(ht, tva, true);
    }

    private record Amounts(double ht, double tva, boolean configured) {}

    @Transactional(readOnly = true)
    public List<BillingInvoice> invoices() {
        return invoiceRepository.findAllByOrderByIssuedAtDesc();
    }

    /** Les documents historiques restent lisibles. Cette version ne crée plus d'avoir et ne change pas un statut à la main. */

    public Map<String, Object> toPlan(CommercialPlan plan) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", plan.getCode());
        row.put("label", plan.getLabel());
        row.put("audience", plan.getAudience());
        row.put("monthlyMad", plan.getMonthlyMad());
        row.put("yearlyMad", plan.getYearlyMad());
        row.put("credits", plan.getCredits());
        row.put("features", plan.getFeatures() == null ? "" : plan.getFeatures());
        row.put("detail", plan.getFeatures() == null ? "" : plan.getFeatures().replace("\n", " · "));
        return row;
    }

    /**
     * La ligne se lit dans une session ouverte : après une écriture, l'entité rendue par save() est
     * détachée et son entreprise reste un proxy non initialisé. On la relit plutôt que de la toucher à l'aveugle.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> toInvoice(BillingInvoice maybeDetached) {
        BillingInvoice invoice = invoiceRepository.findById(maybeDetached.getId()).orElse(maybeDetached);
        Organization org = invoice.getOrganization();
        ma.ensat.hirelink.model.User debtor = invoice.getDebtorUser();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", invoice.getId());
        row.put("number", invoice.getNumber());
        row.put("issuedAt", invoice.getIssuedAt().toString());
        row.put("organization", org == null ? "" : org.getName());
        row.put("organizationId", org == null ? null : org.getId());
        row.put("ice", org == null || org.getIce() == null ? "" : org.getIce());
        row.put("debtorUserId", debtor == null ? null : debtor.getId());
        row.put("debtorName", invoice.getBillToName() == null ? (debtor == null ? "" : debtor.getFullName()) : invoice.getBillToName());
        row.put("debtorEmail", invoice.getBillToEmail() == null ? (debtor == null ? "" : debtor.getEmail()) : invoice.getBillToEmail());
        row.put("plan", invoice.getPlanCode());
        row.put("planLabel", invoice.getPlanLabel());
        row.put("cycle", invoice.getCycle());
        row.put("amountHt", invoice.getAmountHt());
        row.put("tva", invoice.getTva());
        row.put("amountTtc", invoice.getAmountTtc());
        row.put("currency", "MAD");
        row.put("status", invoice.canonicalStatus());
        row.put("storedStatus", invoice.getStatus());
        row.put("vatConfigured", invoice.getVatConfigured());
        row.put("payable", invoice.payable());
        row.put("paidAt", invoice.getPaidAt() == null ? "" : invoice.getPaidAt().toString());
        row.put("transactionRef", invoice.getTransactionRef() == null ? "" : invoice.getTransactionRef());
        row.put("note", invoice.getNote() == null ? "" : invoice.getNote());
        row.put("cancels", invoice.getCancelsInvoiceId() == null ? ""
                : invoiceRepository.findById(invoice.getCancelsInvoiceId()).map(BillingInvoice::getNumber).orElse(""));
        return row;
    }

    /**
     * Numérotation métier : une séquence par entreprise et par mois (HL-AAAAMM-entreprise-n).
     * L'ancien « count() global + 1 » attribuait le même numéro à deux entreprises et échouait sur
     * l'index unique de billing_invoices.number. Le verrou de ligne sur l'entreprise sérialise deux
     * facturations simultanées ; la contrainte d'unicité reste le filet.
     */
    private String nextNumber(Long orgId) {
        organizationRepository.findByIdForUpdate(orgId);
        String month = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneId.of("Africa/Casablanca")).format(Instant.now());
        String prefix = "HL-" + month + "-" + orgId + "-";
        long alreadyIssued = invoiceRepository.countByOrganizationIdAndNumberStartingWith(orgId, prefix);
        return prefix + (alreadyIssued + 1);
    }

    private String nextUserNumber(Long userId) {
        userRepository.findByIdForUpdate(userId);
        String month = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneId.of("Africa/Casablanca")).format(Instant.now());
        String prefix = "HL-" + month + "-U" + userId + "-";
        long alreadyIssued = invoiceRepository.countByDebtorUserIdAndNumberStartingWith(userId, prefix);
        return prefix + (alreadyIssued + 1);
    }

    /** Catalogue des plans créé une fois au démarrage (jamais pendant une requête de lecture). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaults() {
        if (planRepository.count() > 0) return;
        seed("ACCES", "Accès", "RECRUTEUR", 0, 0, 20, "Publication d’offres\nSuivi des candidatures\nJusqu’à 5 sièges\n20 usages IA par mois");
        seed("PREMIUM", "Premium", "RECRUTEUR", 299, 2990, 200, "Jusqu’à 25 sièges\n200 usages IA par mois\nCouronne Premium");
        seed("ESSENTIEL", "Essentiel", "CANDIDAT", 0, 0, 5, "Candidatures\nMatching des compétences\n5 usages IA par mois");
        seed("PERFORMANCE", "Performance", "CANDIDAT", 39, 390, 40, "40 usages IA par mois\nPréparation d’entretien\nRéécriture de profil");
    }

    private void seed(String code, String label, String audience, int month, int year, int credits, String features) {
        CommercialPlan plan = new CommercialPlan();
        plan.setCode(code);
        plan.setLabel(label);
        plan.setAudience(audience);
        plan.setMonthlyMad(month);
        plan.setYearlyMad(year);
        plan.setCredits(credits);
        plan.setFeatures(features);
        planRepository.save(plan);
    }
}
