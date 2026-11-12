package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CommercialPlan;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.repository.BillingInvoiceRepository;
import ma.ensat.hirelink.repository.CommercialPlanRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingQuoteTest {

    @Mock CommercialPlanRepository plans;
    @Mock BillingInvoiceRepository invoices;
    @Mock OrganizationRepository orgs;
    @Mock UserRepository users;
    BillingCatalogService service;

    @BeforeEach
    void setUp() {
        service = new BillingCatalogService(plans, invoices, orgs, users);
        CommercialPlan premium = new CommercialPlan();
        premium.setCode("PREMIUM");
        premium.setLabel("Premium");
        premium.setAudience("RECRUTEUR");
        premium.setMonthlyMad(299);
        premium.setYearlyMad(2990);
        premium.setCredits(200);
        premium.setFeatures("Sieges\nIA");
        when(plans.findByCode("PREMIUM")).thenReturn(Optional.of(premium));
    }

    @Test
    void quoteMonthWithoutVatUsesCatalogTtc() {
        ReflectionTestUtils.setField(service, "vatRate", "");
        Map<String, Object> q = service.quote(PlanTier.PREMIUM, "month");
        assertEquals("Premium", q.get("planLabel"));
        assertEquals("month", q.get("cycle"));
        assertEquals(299, ((Number) q.get("amountTtc")).intValue());
        assertEquals(false, q.get("vatConfigured"));
        assertEquals(true, q.get("payable"));
    }

    @Test
    void quoteYearSplitsHtWhenVatConfigured() {
        ReflectionTestUtils.setField(service, "vatRate", "0.20");
        Map<String, Object> q = service.quote(PlanTier.PREMIUM, "year");
        assertEquals("year", q.get("cycle"));
        assertEquals(2990, ((Number) q.get("amountTtc")).intValue());
        assertEquals(true, q.get("vatConfigured"));
        double ht = ((Number) q.get("amountHt")).doubleValue();
        double tva = ((Number) q.get("tva")).doubleValue();
        assertEquals(2990.0, ht + tva, 0.02);
        assertTrue(ht < 2990);
    }
}
