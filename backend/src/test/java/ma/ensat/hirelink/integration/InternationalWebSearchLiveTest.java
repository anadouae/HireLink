package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.service.JobSearchLexicon;
import ma.ensat.hirelink.service.MultiSourceWebJobDiscovery;
import ma.ensat.hirelink.service.TextMatcher;
import ma.ensat.hirelink.service.WebJobDiscovery;
import ma.ensat.hirelink.service.external.JoobleHttpClient;
import ma.ensat.hirelink.service.external.JoobleJobConnector;
import ma.ensat.hirelink.service.web.ArbeitnowWebJobSource;
import ma.ensat.hirelink.service.web.JobGeoVerifier;
import ma.ensat.hirelink.service.web.JoobleWebJobSource;
import ma.ensat.hirelink.service.web.RemoteOkWebJobSource;
import ma.ensat.hirelink.service.web.RemotiveWebJobSource;
import ma.ensat.hirelink.service.web.WebJobSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preuves LIVE (HTTP réel vers Remotive / Arbeitnow / Remote OK) — pas de mock.
 * Jooble sans clé = SKIPPED (attendu). Aucune écriture DB. Aucun impact 8081.
 */
class InternationalWebSearchLiveTest {

    private static MultiSourceWebJobDiscovery discovery;
    private static boolean networkOk;

    @BeforeAll
    static void wire() {
        networkOk = canReach("https://remotive.com/api/remote-jobs?limit=1");
        JobGeoVerifier geo = new JobGeoVerifier();
        ma.ensat.hirelink.service.web.WebJobFetchCache cache = new ma.ensat.hirelink.service.web.WebJobFetchCache();
        List<WebJobSource> sources = new ArrayList<>();
        sources.add(new RemotiveWebJobSource(geo, cache, true, "https://remotive.com/api/remote-jobs", 21600));
        sources.add(new ArbeitnowWebJobSource(geo, cache, true,
                "https://www.arbeitnow.com/api/job-board-api",
                "https://www.arbeitnow.co.uk/api/job-board-api", 1800));
        sources.add(new RemoteOkWebJobSource(geo, cache, true, "https://remoteok.com/api", 1800));
        // Jooble sans clé — prêt=false
        JoobleHttpClient http = new JoobleHttpClient(2000, 8000, 1_000_000, "HireLink-LiveTest/1.0", "ma.jooble.org,jooble.org");
        JoobleJobConnector jooble = new JoobleJobConnector(http, false, "", "https://ma.jooble.org/api", "", "Maroc", 1);
        sources.add(new JoobleWebJobSource(jooble, geo, true, "MA", "Maroc"));
        // Repli Web sans clé : SKIPPED (attendu en live sans secrets).
        ma.ensat.hirelink.service.web.WebSearchFallbackSource fallback =
                new ma.ensat.hirelink.service.web.WebSearchFallbackSource(geo, cache, true, "", "", 4, 8, 1800);
        discovery = new MultiSourceWebJobDiscovery(sources, geo, new JobSearchLexicon(), fallback, 12);
    }

    @Test
    @Timeout(90)
    void tousLesPaysDeveloppeurRetourneUrlsHttpsDeep() {
        Assumptions.assumeTrue(networkOk, "Réseau Remotive indisponible — test live ignoré");
        WebJobDiscovery.DiscoveryResult r = discovery.search("developer", null, null, 15);
        assertTrue(r.available(), r.message());
        assertFalse(r.jobs().isEmpty(), "Attendu au moins 1 offre Web réelle pour « developer » / ALL");
        for (WebJobDiscovery.DiscoveredJob j : r.jobs()) {
            assertNotNull(j.title());
            assertNotNull(j.originUrl());
            assertTrue(j.originUrl().startsWith("https://"), j.originUrl());
            assertFalse(j.originUrl().matches("https://[^/]+/?"), "pas une home : " + j.originUrl());
            assertNotNull(j.sourceLabel());
        }
        Set<String> codes = r.sources().stream().map(WebJobDiscovery.SourceReport::code).collect(Collectors.toSet());
        assertTrue(codes.contains("REMOTIVE") || codes.contains("REMOTE_OK") || codes.contains("ARBEITNOW"));
        assertTrue(r.coverageSummary().toLowerCase(Locale.ROOT).contains("tous les pays")
                || r.coverageSummary().toLowerCase(Locale.ROOT).contains("all"));
        // Jooble skipped sans clé
        assertTrue(r.sources().stream().anyMatch(s -> "JOOBLE".equals(s.code()) && "SKIPPED".equals(s.status())));
    }

    @Test
    @Timeout(90)
    void filtreAllemagneExclutOffresNonVerifiees() {
        Assumptions.assumeTrue(networkOk, "Réseau indisponible");
        WebJobDiscovery.DiscoveryResult r = discovery.search("engineer", "DE", null, 12);
        assertTrue(r.available() || r.jobs().isEmpty()); // OK même si 0 si sources DE down
        JobGeoVerifier geo = new JobGeoVerifier();
        for (WebJobDiscovery.DiscoveredJob j : r.jobs()) {
            assertTrue(geo.matches(j, "DE", null),
                    "Offre hors filtre DE présentée à tort : " + j.title() + " / " + j.location());
            assertNotNull(j.originUrl());
        }
        // Arbeitnow doit être tenté pour DE
        assertTrue(r.sources().stream().anyMatch(s -> "ARBEITNOW".equals(s.code())));
    }

    @Test
    @Timeout(90)
    void filtreFranceParisOuVideSansFauxPositifs() {
        Assumptions.assumeTrue(networkOk, "Réseau indisponible");
        WebJobDiscovery.DiscoveryResult r = discovery.search("developer", "FR", "Paris", 10);
        JobGeoVerifier geo = new JobGeoVerifier();
        for (WebJobDiscovery.DiscoveredJob j : r.jobs()) {
            assertTrue(geo.matches(j, "FR", "Paris"), j.location() + " vs FR/Paris");
        }
    }

    @Test
    @Timeout(90)
    void marocSansJoobleNeInventePasDoffres() {
        Assumptions.assumeTrue(networkOk, "Réseau indisponible");
        WebJobDiscovery.DiscoveryResult r = discovery.search("developpeur java", "MA", "Casablanca", 10);
        // Remotive/RemoteOK « Worldwide » exclus par géo ; Jooble SKIPPED → souvent 0 résultat
        for (WebJobDiscovery.DiscoveredJob j : r.jobs()) {
            assertTrue(new JobGeoVerifier().matches(j, "MA", "Casablanca"), j.location());
        }
        assertTrue(r.sources().stream().anyMatch(s -> "JOOBLE".equals(s.code()) && "SKIPPED".equals(s.status())));
        assertFalse(r.coverageSummary().isBlank());
    }

    @Test
    void allNeDevientPasMaroc() {
        String summary = discovery.search("", "ALL", null, 0).coverageSummary();
        assertNotNull(summary);
        assertFalse(summary.toLowerCase(Locale.ROOT).contains("filtre géographique : ma"));
    }

    private static boolean canReach(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "HireLink-LiveTest/1.0");
            c.connect();
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
