package ma.ensat.hirelink.service;

import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveryResult;
import ma.ensat.hirelink.service.WebJobDiscovery.SourceReport;
import ma.ensat.hirelink.service.web.JobGeoVerifier;
import ma.ensat.hirelink.service.web.SourceCircuitBreaker;
import ma.ensat.hirelink.service.web.WebJobSource;
import ma.ensat.hirelink.service.web.WebSearchResultCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Agrégateur : isolation des pannes, délai par source, coupe-circuit, cache, doublons entre sources. */
class MultiSourceWebJobDiscoveryTest {

    /** Source factice : chaque appel exécute {@code body} et compte les appels. */
    static final class FakeSource implements WebJobSource {
        final String code;
        final Supplier<List<DiscoveredJob>> body;
        final AtomicInteger calls = new AtomicInteger();

        FakeSource(String code, Supplier<List<DiscoveredJob>> body) {
            this.code = code;
            this.body = body;
        }

        @Override public String code() { return code; }
        @Override public String label() { return code; }
        @Override public String homeUrl() { return "https://example.org"; }
        @Override public String coverageNote() { return ""; }
        @Override public Set<String> countryHints() { return Set.of(); }
        @Override public boolean isReady() { return true; }
        @Override public String notReadyReason() { return ""; }
        @Override public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
            calls.incrementAndGet();
            return body.get();
        }
    }

    static DiscoveredJob job(String source, String title, String company, String location, String url, String contract) {
        return new DiscoveredJob(title, company, location, location, null, contract, "REMOTE", "Java", null,
                url, url, source, source, "https://example.org");
    }

    private static MultiSourceWebJobDiscovery discovery(List<WebJobSource> sources, long timeoutMs,
                                                        SourceCircuitBreaker breaker, WebSearchResultCache cache) {
        return new MultiSourceWebJobDiscovery(sources, new JobGeoVerifier(), new JobSearchLexicon(), null, 15,
                timeoutMs, breaker, cache);
    }

    private static String status(DiscoveryResult r, String code) {
        return r.sources().stream().filter(s -> code.equals(s.code())).map(SourceReport::status).findFirst().orElse("ABSENT");
    }

    @Test
    void uneSourceLenteOuEnPanneNeCassePasLaRecherche() {
        FakeSource ok = new FakeSource("OK_SRC", () -> List.of(
                job("OK_SRC", "Développeur Java", "Acme", "Remote", "https://ok.example/jobs/1", "full_time")));
        FakeSource slow = new FakeSource("SLOW", () -> {
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return List.of(job("SLOW", "Lent", "Slow", "Remote", "https://slow.example/jobs/1", null));
        });
        FakeSource boom = new FakeSource("BOOM", () -> { throw new IllegalStateException("HTTP 503"); });
        FakeSource error = new FakeSource("ERR", () -> { throw new AssertionError("erreur inattendue simulée"); });
        MultiSourceWebJobDiscovery d = discovery(List.of(slow, boom, error, ok), 300,
                new SourceCircuitBreaker(3, 60_000), new WebSearchResultCache(0, 10));

        long t0 = System.currentTimeMillis();
        DiscoveryResult r = d.search("développeur", null, null, 20);
        long took = System.currentTimeMillis() - t0;

        assertTrue(r.available(), "la recherche reste disponible");
        assertEquals(1, r.jobs().size());
        assertEquals("Développeur Java", r.jobs().get(0).title());
        assertEquals("Temps plein", r.jobs().get(0).contractType(), "libellé de contrat en français");
        assertEquals("TIMEOUT", status(r, "SLOW"));
        assertEquals("ERROR", status(r, "BOOM"));
        assertEquals("ERROR", status(r, "ERR"));
        assertEquals("OK", status(r, "OK_SRC"));
        assertTrue(took < 2000, "le délai par source borne la recherche (" + took + " ms)");
    }

    @Test
    void coupeCircuitApresTroisEchecsPuisNouvelEssai() {
        AtomicLong now = new AtomicLong(1_000_000);
        SourceCircuitBreaker breaker = new SourceCircuitBreaker(3, 60_000, now::get);
        AtomicInteger mode = new AtomicInteger(0); // 0 = panne, 1 = rétablie
        FakeSource flaky = new FakeSource("FLAKY", () -> {
            if (mode.get() == 0) throw new IllegalStateException("HTTP 500");
            return List.of(job("FLAKY", "Data analyst", "Beta", "Remote", "https://flaky.example/jobs/1", null));
        });
        MultiSourceWebJobDiscovery d = discovery(List.of(flaky), 1000, breaker, new WebSearchResultCache(0, 10));

        for (int i = 0; i < 3; i++) assertEquals("ERROR", status(d.search("data", null, null, 10), "FLAKY"));
        assertEquals(3, flaky.calls.get());
        assertEquals(SourceCircuitBreaker.State.OPEN, breaker.state("FLAKY"));

        DiscoveryResult skipped = d.search("data", null, null, 10);
        assertEquals("SKIPPED", status(skipped, "FLAKY"));
        assertEquals(3, flaky.calls.get(), "source ouverte : plus appelée");

        now.addAndGet(61_000);
        mode.set(1);
        DiscoveryResult back = d.search("data", null, null, 10);
        assertEquals("OK", status(back, "FLAKY"));
        assertEquals(1, back.jobs().size());
        assertEquals(SourceCircuitBreaker.State.CLOSED, breaker.state("FLAKY"));
    }

    @Test
    void cacheParRequeteNormaliseeEtExpiration() {
        AtomicLong now = new AtomicLong(0);
        WebSearchResultCache cache = new WebSearchResultCache(300_000, 50, now::get);
        FakeSource src = new FakeSource("SRC", () -> List.of(
                job("SRC", "Développeur web", "Gamma", "Remote", "https://src.example/jobs/1", null)));
        MultiSourceWebJobDiscovery d = discovery(List.of(src), 1000, new SourceCircuitBreaker(3, 60_000), cache);

        d.search("Développeur", null, null, 10);
        d.search("  developpeur ", null, null, 10); // même requête une fois normalisée
        assertEquals(1, src.calls.get(), "2e recherche servie par le cache");

        d.search("développeur", "FR", null, 10);
        assertEquals(2, src.calls.get(), "autre pays : autre clé");

        now.addAndGet(300_001);
        d.search("développeur", null, null, 10);
        assertEquals(3, src.calls.get(), "entrée expirée : nouvel appel");
    }

    @Test
    void doublonsEntreSourcesLePremierEstGarde() {
        FakeSource a = new FakeSource("A", () -> List.of(
                job("A", "Backend Developer (H/F)", "Acme Inc.", "Remote", "https://a.example/jobs/1", null),
                job("A", "Frontend Developer", "Acme", "Paris", "https://a.example/jobs/2", null)));
        FakeSource b = new FakeSource("B", () -> List.of(
                job("B", "backend developer", "ACME", "Worldwide", "https://b.example/jobs/9", null),
                job("B", "Frontend Developer", "Acme", "Lyon", "https://b.example/jobs/10", null)));
        MultiSourceWebJobDiscovery d = discovery(List.of(a, b), 1000,
                new SourceCircuitBreaker(3, 60_000), new WebSearchResultCache(0, 10));

        DiscoveryResult r = d.search("developer", null, null, 20);
        long backend = r.jobs().stream().filter(j -> j.title().toLowerCase().contains("backend")).count();
        assertEquals(1, backend, "même intitulé + entreprise + lieu : une seule annonce");
        assertEquals("A", r.jobs().stream().filter(j -> j.title().toLowerCase().contains("backend"))
                .findFirst().orElseThrow().sourceCode(), "la première source est gardée");
    }

    @Test
    void dateEtSalaireDansLeModeleNormalise() {
        Instant at = Instant.parse("2026-11-10T08:00:00Z");
        DiscoveredJob j = job("S", "T", "C", "Remote", "https://s.example/jobs/1", null)
                .withDetails(at, 50000, 70000, "usd");
        assertEquals(at, j.publishedAt());
        assertEquals(50000, j.salaryMin());
        assertEquals(70000, j.salaryMax());
        assertEquals("USD", j.salaryCurrency());
        assertEquals(null, j.salaryPeriod());
        DiscoveredJob yearly = j.withDetails(at, 50000, 70000, "usd", "yearly");
        assertEquals("YEAR", yearly.salaryPeriod());
        DiscoveredJob none = j.withDetails(null, 0, null, "USD");
        assertEquals(null, none.salaryMin());
        assertEquals(null, none.salaryCurrency(), "pas de devise sans montant");
        assertEquals(null, none.salaryPeriod(), "pas de période sans montant");
    }
}
