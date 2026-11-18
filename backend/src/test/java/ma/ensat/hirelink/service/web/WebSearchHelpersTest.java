package ma.ensat.hirelink.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Briques de l'agrégateur : coupe-circuit, cache, clé de doublon, libellés de contrat, dates, période de salaire. */
class WebSearchHelpersTest {

    @Test
    void coupeCircuitUnSeulEssaiApresLaPause() {
        AtomicLong now = new AtomicLong(0);
        SourceCircuitBreaker b = new SourceCircuitBreaker(3, 10_000, now::get);
        b.recordFailure("S");
        b.recordFailure("S");
        assertTrue(b.allow("S"), "2 échecs : encore fermé");
        b.recordSuccess("S");
        b.recordFailure("S");
        b.recordFailure("S");
        assertTrue(b.allow("S"), "un succès remet le compteur à zéro");
        b.recordFailure("S");
        assertFalse(b.allow("S"));
        assertEquals(10, b.secondsUntilRetry("S"));
        now.set(10_000);
        assertEquals(SourceCircuitBreaker.State.HALF_OPEN, b.state("S"));
        assertTrue(b.allow("S"), "essai autorisé après la pause");
        assertFalse(b.allow("S"), "un seul essai à la fois");
        b.recordFailure("S");
        assertEquals(SourceCircuitBreaker.State.OPEN, b.state("S"), "essai raté : nouvelle pause");
        assertTrue(b.allow("AUTRE"), "les autres sources ne sont pas touchées");
    }

    @Test
    void cacheBorneEtTtlZeroDesactive() {
        AtomicLong now = new AtomicLong(0);
        WebSearchResultCache c = new WebSearchResultCache(1000, 10, now::get);
        for (int i = 0; i < 25; i++) c.put("k" + i, List.of());
        assertTrue(c.size() <= 10);
        WebSearchResultCache off = new WebSearchResultCache(0, 10);
        off.put("k", List.of());
        assertNull(off.get("k"));
        assertEquals(WebSearchResultCache.key("S", "Développeur  Java", null, null, 10),
                WebSearchResultCache.key("S", "developpeur java", "all", "", 10));
        assertNotEquals(WebSearchResultCache.key("S", "java", "FR", null, 10),
                WebSearchResultCache.key("S", "java", "MA", null, 10));
    }

    @Test
    void cleDeDoublonTitreEntrepriseLieu() {
        assertEquals(WebJobDedupe.key("Développeur Java (H/F)", "Acme SARL", "Remote"),
                WebJobDedupe.key("developpeur java", "ACME", "Worldwide"));
        assertNotEquals(WebJobDedupe.key("Développeur Java", "Acme", "Paris"),
                WebJobDedupe.key("Développeur Java", "Acme", "Lyon"));
        assertNotEquals(WebJobDedupe.key("Développeur Java", "Acme", "Paris"),
                WebJobDedupe.key("Développeur Java", "Beta", "Paris"));
    }

    @Test
    void libellesDeContratEnFrancais() {
        assertEquals("Temps plein", WebContractLabels.french("full_time"));
        assertEquals("Temps plein", WebContractLabels.french("Full-Time"));
        assertEquals("Temps partiel", WebContractLabels.french("part_time"));
        assertEquals("Freelance", WebContractLabels.french("contract"));
        assertEquals("Stage", WebContractLabels.french("Internship"));
        assertEquals("CDI", WebContractLabels.french("permanent"));
        assertEquals("CDD", WebContractLabels.french("temporary"));
        assertEquals("Temps plein", WebContractLabels.french("berufserfahren, full time"));
        assertEquals("CDI", WebContractLabels.french("CDI"));
        assertNull(WebContractLabels.french("Back-End Programming"), "catégorie métier : non précisé");
        assertNull(WebContractLabels.french(null));
    }

    @Test
    void datesDesSources() throws Exception {
        ObjectMapper m = new ObjectMapper();
        Instant at = Instant.parse("2026-11-10T08:00:00Z");
        assertEquals(at, WebJobFields.date(m.readTree("1794297600")));
        assertEquals(at, WebJobFields.date("2026-11-10T08:00:00"));
        assertEquals(at, WebJobFields.date("2026-11-10 08:00:00"));
        assertEquals(at, WebJobFields.date("2026-11-10T09:00:00+01:00"));
        assertEquals(at, WebJobFields.date("Tue, 10 Nov 2026 08:00:00 +0000"));
        assertNull(WebJobFields.date("demain"));
        assertNull(WebJobFields.date(m.readTree("99999999999999")), "date future : non fiable");
        assertEquals(52000, WebJobFields.amount(m.readTree("\"52000\"")));
        assertNull(WebJobFields.amount(m.readTree("0")));
    }

    @Test
    void periodeDeSalaireUniquementSiLeTexteLaDit() {
        assertEquals("YEAR", WebJobFields.period("yearly"));
        assertEquals("YEAR", WebJobFields.period("annual"));
        assertEquals("YEAR", WebJobFields.period("YEAR"));
        assertEquals("YEAR", WebJobFields.period("$80,000 per annum"));
        assertEquals("YEAR", WebJobFields.period("50 000 € par an"));
        assertEquals("MONTH", WebJobFields.period("monthly"));
        assertEquals("MONTH", WebJobFields.period("$3.5k–$5k/month"));
        assertEquals("HOUR", WebJobFields.period("hourly"));
        assertEquals("HOUR", WebJobFields.period("$14 - $20/hour"));
        assertEquals("WEEK", WebJobFields.period("per week"));
        assertEquals("DAY", WebJobFields.period("par jour"));
        assertNull(WebJobFields.period("$170k - $200k"), "montant sans période : non précisée");
        assertNull(WebJobFields.period("OTE $25k - $35k"));
        assertNull(WebJobFields.period((String) null));
        assertNull(WebJobFields.period(""));
    }

    @Test
    void salaireTexteLibreRemotive() {
        WebJobFields.ParsedPay range = WebJobFields.pay("$170k - $200k");
        assertEquals(170000, range.min());
        assertEquals(200000, range.max());
        assertEquals("USD", range.currency());
        assertNull(range.period(), "pas de période dans le texte");

        WebJobFields.ParsedPay hour = WebJobFields.pay("$14 - $20/hour");
        assertEquals(14, hour.min());
        assertEquals(20, hour.max());
        assertEquals("USD", hour.currency());
        assertEquals("HOUR", hour.period());

        WebJobFields.ParsedPay month = WebJobFields.pay("$3.5k–$5k/month");
        assertEquals(3500, month.min());
        assertEquals(5000, month.max());
        assertEquals("USD", month.currency());
        assertEquals("MONTH", month.period());

        WebJobFields.ParsedPay single = WebJobFields.pay("$14/hour");
        assertEquals(14, single.min());
        assertNull(single.max());
        assertEquals("HOUR", single.period());

        WebJobFields.ParsedPay euroYear = WebJobFields.pay("€40,000 per annum");
        assertEquals(40000, euroYear.min());
        assertEquals("EUR", euroYear.currency());
        assertEquals("YEAR", euroYear.period());

        WebJobFields.ParsedPay vague = WebJobFields.pay("70K-80K");
        assertEquals(70000, vague.min());
        assertEquals(80000, vague.max());
        assertNull(vague.currency());
        assertNull(vague.period());

        assertNull(WebJobFields.pay("").min());
        assertNull(WebJobFields.pay(null).min());
    }
}
