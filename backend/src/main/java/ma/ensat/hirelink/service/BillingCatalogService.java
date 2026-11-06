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

    /**
     * L'encaissement se constate ici, pas dans la comptabilité : EMISE = pas encore réglée,
     * REGLEE = encaissement confirmé. Une facture annulée par un avoir n'est plus modifiable.
     */
    @Transactional
    public BillingInvoice markStatus(Long id, String status, String note) {
        BillingInvoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facture introuvable"));
        if ("AVOIR".equals(inv.getStatus()) || "ANNULEE".equals(inv.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette facture est déjà soldée par un avoir.");
        }
        if (!"REGLEE".equalsIgnoreCase(status) && !"EMISE".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut de facture inconnu.");
        }
        inv.setStatus("REGLEE".equalsIgnoreCase(status) ? "REGLEE" : "EMISE");
        if (note != null) inv.setNote(trimTo(note, 255));
        return invoiceRepository.save(inv);
    }

    /**
     * Un avoir est une facture négative qui en annule une autre : on ne réécrit pas l'originale,
     * la piste d'audit reste lisible des deux côtés.
     */
    @Transactional
    public BillingInvoice creditNote(Long id, String motif) {
        BillingInvoice orig = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facture introuvable"));
        if ("AVOIR".equals(orig.getStatus()) || "ANNULEE".equals(orig.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette facture a déjà fait l'objet d'un avoir.");
        }
        String reason = trimTo(motif, 255);
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez le motif de l'avoir.");
        }
        BillingInvoice avoir = new BillingInvoice();
        avoir.setNumber(orig.getNumber() + "-A");
        avoir.setOrganization(orig.getOrganization());
        avoir.setPlanCode(orig.getPlanCode());
        avoir.setPlanLabel(orig.getPlanLabel());
        avoir.setCycle(orig.getCycle());
        avoir.setAmountHt(-orig.getAmountHt());
        avoir.setTva(-orig.getTva());
        avoir.setAmountTtc(-orig.getAmountTtc());
        avoir.setStatus("AVOIR");
        avoir.setNote(reason);
        avoir.setCancelsInvoiceId(orig.getId());
        avoir.setIssuedAt(Instant.now());
        invoiceRepository.save(avoir);
        orig.setStatus("ANNULEE");
        orig.setNote(reason);
        invoiceRepository.save(orig);
        return avoir;
    }

    private static String trimTo(String raw, int max) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
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

    /**
     * La ligne se lit dans une session ouverte : après une écriture, l'entité rendue par save() est
     * détachée et son entreprise reste un proxy non initialisé. On la relit plutôt que de la toucher à l'aveugle.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> toInvoice(BillingInvoice maybeDetached) {
        BillingInvoice invoice = invoiceRepository.findById(maybeDetached.getId()).orElse(maybeDetached);
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
        row.put("note", invoice.getNote() == null ? "" : invoice.getNote());
        row.put("cancels", invoice.getCancelsInvoiceId() == null ? ""
                : invoiceRepository.findById(invoice.getCancelsInvoiceId()).map(BillingInvoice::getNumber).orElse(""));
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
