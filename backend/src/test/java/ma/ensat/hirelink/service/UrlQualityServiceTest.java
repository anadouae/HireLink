package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlQualityServiceTest {

    private final UrlQualityService service = new UrlQualityService();

    @Test
    void annonceProfondeEstDeep() {
        var a = service.classifyOrigin("https://boards.greenhouse.io/acme/jobs/1234567");
        assertEquals(UrlQuality.DEEP, a.quality());
        assertTrue(a.quality().isDirectAccessTrusted());
        assertTrue(a.quality().isPubliclyListable());
    }

    @Test
    void homepageEstGenericHome() {
        var a = service.classifyOrigin("https://www.rekrute.com/");
        assertEquals(UrlQuality.GENERIC_HOME, a.quality());
        assertTrue(a.quality().isQuarantineCandidate());
        assertFalse(a.quality().isDirectAccessTrusted());
    }

    @Test
    void indexCarrieresSeulEstGeneric() {
        assertEquals(UrlQuality.GENERIC_HOME, service.classifyOrigin("https://example.com/careers").quality());
        assertEquals(UrlQuality.GENERIC_HOME, service.classifyOrigin("https://example.com/jobs/").quality());
    }

    @Test
    void urlMalformeeOuNonHttpsEstInvalid() {
        assertEquals(UrlQuality.INVALID, service.classifyOrigin("").quality());
        assertEquals(UrlQuality.INVALID, service.classifyOrigin("not a url").quality());
        assertEquals(UrlQuality.INVALID, service.classifyOrigin("http://example.com/jobs/1").quality());
        assertEquals(UrlQuality.INVALID, service.classifyOrigin("javascript:alert(1)").quality());
        assertEquals(UrlQuality.INVALID, service.classifyOrigin("https://localhost/jobs/1").quality());
    }

    @Test
    void slugAmbiguResteUnknownSansFauxPositifDeep() {
        var a = service.classifyOrigin("https://careers.example.com/join-our-product-team");
        assertEquals(UrlQuality.UNKNOWN, a.quality());
        assertFalse(a.quality().isDirectAccessTrusted());
        assertTrue(a.quality().isPubliclyListable());
    }

    @Test
    void redirectionVersHomepageDegradeLaQualite() {
        var a = service.classifyAfterRedirect(
                "https://jobs.example.com/posting/999",
                "https://www.example.com/",
                302);
        assertEquals(UrlQuality.GENERIC_HOME, a.quality());
    }

    @Test
    void inaccessibleEstInvalid() {
        var a = service.classifyAfterRedirect(
                "https://jobs.example.com/posting/999",
                null,
                404);
        assertEquals(UrlQuality.INVALID, a.quality());
    }

    @Test
    void applyAbsentResteUnknownSansInvention() {
        var a = service.classifyApply(null);
        assertEquals(UrlQuality.UNKNOWN, a.quality());
        assertEquals("Aucun lien de candidature fourni", a.reason());
    }
}
