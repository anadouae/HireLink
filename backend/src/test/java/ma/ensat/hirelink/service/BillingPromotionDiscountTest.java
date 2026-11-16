package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingPromotion;
import ma.ensat.hirelink.repository.BillingPromotionEventRepository;
import ma.ensat.hirelink.repository.BillingPromotionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BillingPromotionDiscountTest {

    private BillingPromotionService svc() {
        return new BillingPromotionService(
                mock(BillingPromotionRepository.class),
                mock(BillingPromotionEventRepository.class),
                mock(BillingCatalogService.class));
    }

    @Test
    void percentAndFixed() {
        BillingPromotionService s = svc();
        BillingPromotion p = new BillingPromotion();
        p.setDiscountType(BillingPromotion.DiscountType.PERCENT);
        p.setDiscountValue(10);
        assertEquals(269, s.applyDiscount(299, p));

        p.setDiscountType(BillingPromotion.DiscountType.FIXED);
        p.setDiscountValue(50);
        assertEquals(249, s.applyDiscount(299, p));
    }

    @Test
    void rejectsNegative() {
        BillingPromotionService s = svc();
        BillingPromotion p = new BillingPromotion();
        p.setDiscountType(BillingPromotion.DiscountType.FIXED);
        p.setDiscountValue(500);
        assertThrows(ResponseStatusException.class, () -> s.applyDiscount(299, p));
    }
}
