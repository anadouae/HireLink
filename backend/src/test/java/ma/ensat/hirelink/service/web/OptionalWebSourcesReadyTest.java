package ma.ensat.hirelink.service.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sans clés env : Adzuna et repli Tavily/Serper restent non prêts (clone local OK). */
class OptionalWebSourcesReadyTest {

    @Test
    void adzunaSansClesEstSkip() {
        AdzunaWebJobSource src = new AdzunaWebJobSource(
                new JobGeoVerifier(), new WebJobFetchCache(), true, "", "", 
                "https://api.adzuna.com/v1/api/jobs", "fr", 1800);
        assertFalse(src.isReady());
        assertTrue(src.notReadyReason().contains("HIRELINK_ADZUNA"));
        assertTrue(src.fetch("java", "FR", null, 5).isEmpty());
    }

    @Test
    void webFallbackSansClesEstSkip() {
        WebSearchFallbackSource fb = new WebSearchFallbackSource(
                new JobGeoVerifier(), new WebJobFetchCache(), true, "", "", 4, 8, 1800);
        assertFalse(fb.isReady());
        assertTrue(fb.notReadyReason().contains("TAVILY") || fb.notReadyReason().contains("SERPER"));
        assertTrue(fb.search("stage pfe", "MA", null, 5).isEmpty());
    }
}
