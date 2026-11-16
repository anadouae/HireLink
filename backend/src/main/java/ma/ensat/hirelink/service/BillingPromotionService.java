package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingPromotion;
import ma.ensat.hirelink.model.BillingPromotionEvent;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.BillingPromotionEventRepository;
import ma.ensat.hirelink.repository.BillingPromotionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BillingPromotionService {

    private final BillingPromotionRepository promotions;
    private final BillingPromotionEventRepository events;
    private final BillingCatalogService catalog;

    public BillingPromotionService(BillingPromotionRepository promotions,
                                   BillingPromotionEventRepository events,
                                   BillingCatalogService catalog) {
        this.promotions = promotions;
        this.events = events;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return promotions.findAllByOrderByUpdatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history() {
        return events.findTop200ByOrderByCreatedAtDesc().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("promotionId", e.getPromotionId());
            row.put("action", e.getAction());
            row.put("actorUserId", e.getActorUserId());
            row.put("invoiceId", e.getInvoiceId());
            row.put("detail", e.getDetail());
            row.put("createdAt", e.getCreatedAt().toString());
            return row;
        }).toList();
    }

    @Transactional
    public Map<String, Object> create(User admin, Map<String, Object> body) {
        BillingPromotion p = fromBody(new BillingPromotion(), body, true);
        p.setCreatedByUserId(admin.getId());
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        p = promotions.save(p);
        log(admin, p, "CREATED", null, "Création promo " + p.getCode());
        return toView(p);
    }

    @Transactional
    public Map<String, Object> update(User admin, Long id, Map<String, Object> body) {
        BillingPromotion p = promotions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion introuvable"));
        fromBody(p, body, false);
        p.setUpdatedAt(Instant.now());
        p = promotions.save(p);
        log(admin, p, "UPDATED", null, "Mise à jour promo " + p.getCode());
        return toView(p);
    }

    @Transactional
    public Map<String, Object> setActive(User admin, Long id, boolean active) {
        BillingPromotion p = promotions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion introuvable"));
        p.setActive(active);
        p.setUpdatedAt(Instant.now());
        promotions.save(p);
        log(admin, p, active ? "ACTIVATED" : "DEACTIVATED", null, p.getCode());
        return toView(p);
    }

    /** Prévisualisation TTC après promo (sans écriture). */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(String planCode, String cycle, String promoCode) {
        PlanTier tier = parsePaidTier(planCode);
        Map<String, Object> quote = catalog.quote(tier, cycle);
        int listTtc = ((Number) quote.get("amountTtc")).intValue();
        BillingPromotion promo = resolveUsable(promoCode, tier.name(), Instant.now());
        int finalTtc = applyDiscount(listTtc, promo);
        quote.put("listAmountTtc", listTtc);
        quote.put("promoCode", promo.getCode());
        quote.put("promoLabel", promo.getLabel());
        quote.put("discountType", promo.getDiscountType().name());
        quote.put("discountValue", promo.getDiscountValue());
        quote.put("amountTtc", finalTtc);
        // Recalcule HT/TVA sur le TTC final (hypothèse 20 % configurable).
        Map<String, Object> recomputed = catalog.quoteAmountsFromTtc(finalTtc);
        quote.put("amountHt", recomputed.get("amountHt"));
        quote.put("tva", recomputed.get("tva"));
        quote.put("vatConfigured", recomputed.get("vatConfigured"));
        quote.put("vatHypothesisNote",
                "TVA 20 % = hypothèse de configuration ; applicabilité juridique non confirmée.");
        return quote;
    }

    public BillingPromotion requireUsable(String promoCode, String planCode, Instant at) {
        return resolveUsable(promoCode, planCode, at);
    }

    public int applyDiscount(int listTtc, BillingPromotion promo) {
        if (listTtc <= 0) return 0;
        int discounted;
        if (promo.getDiscountType() == BillingPromotion.DiscountType.PERCENT) {
            discounted = (int) Math.round(listTtc * (1.0 - (promo.getDiscountValue() / 100.0)));
        } else {
            discounted = listTtc - promo.getDiscountValue();
        }
        if (discounted < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La promotion produirait un prix négatif.");
        }
        if (discounted >= listTtc && listTtc > 0 && promo.getDiscountValue() > 0
                && promo.getDiscountType() == BillingPromotion.DiscountType.PERCENT
                && promo.getDiscountValue() >= 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Réduction 100 % interdite pour une formule payante.");
        }
        return discounted;
    }

    private BillingPromotion resolveUsable(String promoCode, String planCode, Instant at) {
        if (promoCode == null || promoCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code promotion manquant.");
        }
        BillingPromotion p = promotions.findByCodeIgnoreCase(promoCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion introuvable."));
        if (!p.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion désactivée.");
        }
        if (!p.getPlanCode().equalsIgnoreCase(planCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion non applicable à cette formule.");
        }
        if (p.getValidFrom() != null && at.isBefore(p.getValidFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion pas encore valide.");
        }
        if (p.getValidTo() != null && !at.isBefore(p.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion expirée.");
        }
        return p;
    }

    private BillingPromotion fromBody(BillingPromotion p, Map<String, Object> body, boolean creating) {
        String code = str(body.get("code"));
        String label = str(body.get("label"));
        String planCode = str(body.get("planCode"));
        String type = str(body.get("discountType")).toUpperCase(Locale.ROOT);
        int value = intOf(body.get("discountValue"), -1);
        if (creating) {
            if (code.isBlank() || !code.matches("[A-Za-z0-9_-]{3,40}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code promotion invalide (3–40, A-Z 0-9 _ -).");
            }
            if (promotions.findByCodeIgnoreCase(code).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Code promotion déjà utilisé.");
            }
            p.setCode(code.toUpperCase(Locale.ROOT));
        }
        if (!label.isBlank()) p.setLabel(label);
        else if (creating) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Libellé requis.");

        if (!planCode.isBlank()) {
            parsePaidTier(planCode);
            p.setPlanCode(planCode.toUpperCase(Locale.ROOT));
        } else if (creating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formule cible requise.");
        }

        if (!type.isBlank()) {
            try {
                p.setDiscountType(BillingPromotion.DiscountType.valueOf(type));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de réduction : PERCENT ou FIXED.");
            }
        } else if (creating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de réduction requis.");
        }

        if (value >= 0) p.setDiscountValue(value);
        else if (creating) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valeur de réduction requise.");

        validateDiscount(p);

        if (body.containsKey("validFrom")) p.setValidFrom(parseInstant(body.get("validFrom")));
        if (body.containsKey("validTo")) p.setValidTo(parseInstant(body.get("validTo")));
        if (p.getValidFrom() != null && p.getValidTo() != null && !p.getValidTo().isAfter(p.getValidFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fin de validité doit être après le début.");
        }
        if (body.containsKey("active")) p.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
        if (body.containsKey("stackingPolicy")) {
            try {
                p.setStackingPolicy(BillingPromotion.StackingPolicy.valueOf(str(body.get("stackingPolicy")).toUpperCase(Locale.ROOT)));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Règle de cumul invalide.");
            }
        }
        if (body.containsKey("renewalPolicy")) {
            try {
                p.setRenewalPolicy(BillingPromotion.RenewalPolicy.valueOf(str(body.get("renewalPolicy")).toUpperCase(Locale.ROOT)));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Règle de renouvellement invalide.");
            }
        }
        if (body.containsKey("note")) p.setNote(str(body.get("note")));
        return p;
    }

    private void validateDiscount(BillingPromotion p) {
        if (p.getDiscountType() == BillingPromotion.DiscountType.PERCENT) {
            if (p.getDiscountValue() < 1 || p.getDiscountValue() > 90) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pourcentage autorisé : 1 à 90.");
            }
        } else if (p.getDiscountValue() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Montant fixe invalide.");
        }
        // Prix négatif : contrôlé à la prévisualisation / application selon le TTC catalogue.
        int list = PlanTier.valueOf(p.getPlanCode()).defaultMonthlyMadTtc();
        if (p.getDiscountType() == BillingPromotion.DiscountType.FIXED && p.getDiscountValue() >= list && list > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La réduction fixe doit rester inférieure au prix catalogue (" + list + " MAD TTC).");
        }
    }

    private PlanTier parsePaidTier(String code) {
        try {
            PlanTier t = PlanTier.valueOf(code.trim().toUpperCase(Locale.ROOT));
            t = t == PlanTier.PREMIUM ? PlanTier.PROFESSIONNEL : t;
            if (!t.isPaid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les promotions ciblent une formule payante.");
            }
            return t;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code formule inconnu.");
        }
    }

    private void log(User admin, BillingPromotion p, String action, Long invoiceId, String detail) {
        BillingPromotionEvent e = new BillingPromotionEvent();
        e.setPromotionId(p.getId());
        e.setAction(action);
        e.setActorUserId(admin == null ? null : admin.getId());
        e.setInvoiceId(invoiceId);
        e.setDetail(detail);
        e.setCreatedAt(Instant.now());
        events.save(e);
    }

    public void logApplied(User actor, BillingPromotion p, Long invoiceId, String detail) {
        log(actor, p, "APPLIED", invoiceId, detail);
    }

    public Map<String, Object> toView(BillingPromotion p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("code", p.getCode());
        row.put("label", p.getLabel());
        row.put("planCode", p.getPlanCode());
        row.put("discountType", p.getDiscountType().name());
        row.put("discountValue", p.getDiscountValue());
        row.put("validFrom", p.getValidFrom() == null ? "" : p.getValidFrom().toString());
        row.put("validTo", p.getValidTo() == null ? "" : p.getValidTo().toString());
        row.put("active", p.isActive());
        row.put("stackingPolicy", p.getStackingPolicy().name());
        row.put("renewalPolicy", p.getRenewalPolicy().name());
        row.put("note", p.getNote() == null ? "" : p.getNote());
        row.put("updatedAt", p.getUpdatedAt().toString());
        try {
            Map<String, Object> preview = preview(p.getPlanCode(), "month", p.getCode());
            row.put("previewMonthlyTtc", preview.get("amountTtc"));
            row.put("previewListTtc", preview.get("listAmountTtc"));
        } catch (Exception ex) {
            row.put("previewMonthlyTtc", null);
            row.put("previewError", ex.getMessage());
        }
        return row;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static int intOf(Object o, int d) {
        if (o == null || String.valueOf(o).isBlank()) return d;
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return d; }
    }
    private static Instant parseInstant(Object o) {
        String s = str(o);
        if (s.isBlank()) return null;
        try { return Instant.parse(s); } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date invalide (ISO-8601 attendu).");
        }
    }
}
