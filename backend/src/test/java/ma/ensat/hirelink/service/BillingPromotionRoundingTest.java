package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingPromotion;
import ma.ensat.hirelink.repository.BillingPromotionEventRepository;
import ma.ensat.hirelink.repository.BillingPromotionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Vague 6 — arrondis promotions / cohérence TTC.
 * Règle documentée : Math.round sur le pourcentage ; prix négatif rejeté.
 */
class BillingPromotionRoundingTest {

    private BillingPromotionService svc() {
        return new BillingPromotionService(
                mock(BillingPromotionRepository.class),
                mock(BillingPromotionEventRepository.class),
                mock(BillingCatalogService.class));
    }

    @Test
    void percentHalfUp_roundToNearestMad() {
        BillingPromotionService s = svc();
        BillingPromotion p = new BillingPromotion();
        p.setDiscountType(BillingPromotion.DiscountType.PERCENT);
        // 15 % de 299 = 44,85 → 299 - 44,85 = 254,15 → round 254
        p.setDiscountValue(15);
        assertEquals(254, s.applyDiscount(299, p));
        // 33 % de 39 = 12,87 → 26,13 → 26
        p.setDiscountValue(33);
        assertEquals(26, s.applyDiscount(39, p));
    }

    @Test
    void fixedNeverBelowZero() {
        BillingPromotionService s = svc();
        BillingPromotion p = new BillingPromotion();
        p.setDiscountType(BillingPromotion.DiscountType.FIXED);
        p.setDiscountValue(299);
        assertEquals(0, s.applyDiscount(299, p));
        p.setDiscountValue(300);
        assertThrows(ResponseStatusException.class, () -> s.applyDiscount(299, p));
    }

    @Test
    void catalogHtTvaSumToTtc() throws Exception {
        BillingCatalogService catalog = new BillingCatalogService(
                mock(ma.ensat.hirelink.repository.CommercialPlanRepository.class),
                mock(ma.ensat.hirelink.repository.BillingInvoiceRepository.class),
                mock(ma.ensat.hirelink.repository.OrganizationRepository.class),
                mock(ma.ensat.hirelink.repository.UserRepository.class));
        var field = BillingCatalogService.class.getDeclaredField("vatRate");
        field.setAccessible(true);
        field.set(catalog, "0.20");
        var amounts = catalog.quoteAmountsFromTtc(299);
        double ht = ((Number) amounts.get("amountHt")).doubleValue();
        double tva = ((Number) amounts.get("tva")).doubleValue();
        assertEquals(299.0, Math.round((ht + tva) * 100.0) / 100.0, 0.001);
        assertTrue(ht > 0 && tva > 0);
    }
}
