package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobGeoVerifierTest {

    private final JobGeoVerifier geo = new JobGeoVerifier();

    private DiscoveredJob job(String loc, String country) {
        return new DiscoveredJob("Title", "Co", loc, loc, country, null, null, null, null,
                "https://example.com/jobs/1", "https://example.com/jobs/1", "T", "T", "https://example.com");
    }

    @Test
    void allAccepteTout() {
        assertTrue(geo.matches(job("Worldwide", null), "ALL", null));
        assertTrue(geo.matches(job("Berlin", "DE"), null, null));
    }

    @Test
    void paysPrecisRejetteWorldwide() {
        assertFalse(geo.matches(job("Worldwide", null), "FR", null));
        assertFalse(geo.matches(job("Remote", null), "MA", "Casablanca"));
    }

    @Test
    void allemagneVerifiee() {
        assertTrue(geo.matches(job("Berlin / Remote", null), "DE", null));
        assertTrue(geo.matches(job("Munich", "DE"), "DE", null));
        assertFalse(geo.matches(job("Casablanca", "MA"), "DE", null));
    }

    @Test
    void allNeSeComportPasCommeMaroc() {
        assertTrue(geo.isWorldwideFilter("ALL"));
        assertTrue(geo.isWorldwideFilter("*"));
        assertFalse(geo.isWorldwideFilter("MA"));
    }
}
