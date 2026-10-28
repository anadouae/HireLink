package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.model.CommercialPlan;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.repository.BillingInvoiceRepository;
import ma.ensat.hirelink.repository.CommercialPlanRepository;
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

@Service
public class BillingCatalogService {

    private final CommercialPlanRepository planRepository;
    private final BillingInvoiceRepository invoiceRepository;

    public BillingCatalogService(CommercialPlanRepository planRepository, BillingInvoiceRepository invoiceRepository) {
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
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

    /** La facture suit le changement de plan. Elle n’est pas créée à la main. */
    @Transactional
    public BillingInvoice issueForPlanChange(Organization org, PlanTier tier, String cycle) {
        CommercialPlan plan = require(tier);
        String period = "year".equalsIgnoreCase(cycle) ? "year" : "month";
        int ttc = "year".equals(period) ? plan.getYearlyMad() : plan.getMonthlyMad();
        double ht = Math.round((ttc / 1.2) * 100.0) / 100.0;
        double tva = Math.round((ttc - ht) * 100.0) / 100.0;
        BillingInvoice invoice = new BillingInvoice();
        invoice.setNumber(nextNumber(org.getId()));
        invoice.setOrganization(org);
        invoice.setPlanCode(plan.getCode());
        invoice.setPlanLabel(plan.getLabel());
        invoice.setCycle(period);
        invoice.setAmountHt(ht);
        invoice.setTva(tva);
        invoice.setAmountTtc(ttc);
        invoice.setStatus(ttc == 0 ? "OFFERTE" : "REGLEE");
        invoice.setIssuedAt(Instant.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public List<BillingInvoice> invoices() {
        return invoiceRepository.findAllByOrderByIssuedAtDesc();
    }

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

    public Map<String, Object> toInvoice(BillingInvoice invoice) {
        Organization org = invoice.getOrganization();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", invoice.getId());
        row.put("number", invoice.getNumber());
        row.put("issuedAt", invoice.getIssuedAt().toString());
        row.put("organization", org.getName());
        row.put("organizationId", org.getId());
        row.put("ice", org.getIce() == null ? "" : org.getIce());
        row.put("plan", invoice.getPlanCode());
        row.put("planLabel", invoice.getPlanLabel());
        row.put("cycle", invoice.getCycle());
        row.put("amountHt", invoice.getAmountHt());
        row.put("tva", invoice.getTva());
        row.put("amountTtc", invoice.getAmountTtc());
        row.put("currency", "MAD");
        row.put("status", invoice.getStatus());
        return row;
    }

    private String nextNumber(Long orgId) {
        String day = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Africa/Casablanca")).format(Instant.now());
        return "HL-" + day + "-" + orgId + "-" + (invoiceRepository.count() + 1);
    }

    /** Catalogue des plans créé une fois au démarrage (jamais pendant une requête de lecture). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaults() {
        if (planRepository.count() > 0) return;
        seed("ACCES", "Accès", "RECRUTEUR", 0, 0, 20, "Publication d’offres\nSuivi des candidatures\n1 recruteur");
        seed("PREMIUM", "Premium", "RECRUTEUR", 299, 2990, 200, "Équipe\nOffres mises en avant\nAlertes de délai");
        seed("ESSENTIEL", "Essentiel", "CANDIDAT", 0, 0, 5, "Candidatures\nMatching des compétences");
        seed("PERFORMANCE", "Performance", "CANDIDAT", 39, 390, 40, "Alertes\nPréparation d’entretien");
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
