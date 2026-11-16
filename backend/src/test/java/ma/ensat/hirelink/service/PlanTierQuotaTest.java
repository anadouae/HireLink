package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanTierQuotaTest {

    @Test
    void mappingLegacyAliases() {
        assertEquals(PlanTier.ESSENTIEL, PlanTier.normalize(PlanTier.ACCES, Role.RECRUTEUR));
        assertEquals(PlanTier.PROFESSIONNEL, PlanTier.normalize(PlanTier.PREMIUM, Role.RECRUTEUR));
        assertEquals(PlanTier.GRATUIT, PlanTier.normalize(PlanTier.ESSENTIEL, Role.CANDIDAT));
        assertEquals(PlanTier.GRATUIT, PlanTier.normalize(null, Role.CANDIDAT));
    }

    @Test
    void recruiterQuotas() {
        assertEquals(1, PlanTier.ESSENTIEL.seatLimit());
        assertEquals(3, PlanTier.ESSENTIEL.activeOfferLimit());
        assertEquals(5, PlanTier.PROFESSIONNEL.seatLimit());
        assertEquals(20, PlanTier.PROFESSIONNEL.activeOfferLimit());
        assertEquals(25, PlanTier.ENTREPRISE.seatLimit());
        assertTrue(PlanTier.ENTREPRISE.unlimitedOffers());
        assertEquals(100, PlanTier.PROFESSIONNEL.defaultAiCredits());
        assertEquals(200, PlanTier.ENTREPRISE.defaultAiCredits());
        assertEquals(299, PlanTier.PROFESSIONNEL.defaultMonthlyMadTtc());
        assertEquals(699, PlanTier.ENTREPRISE.defaultMonthlyMadTtc());
    }

    @Test
    void candidateQuotas() {
        assertEquals(5, PlanTier.GRATUIT.defaultAiCredits());
        assertTrue(PlanTier.GRATUIT.rollingThirtyDayCredits());
        assertEquals(40, PlanTier.PERFORMANCE.defaultAiCredits());
        assertEquals(39, PlanTier.PERFORMANCE.defaultMonthlyMadTtc());
        assertFalse(PlanTier.PERFORMANCE.rollingThirtyDayCredits());
    }
}
