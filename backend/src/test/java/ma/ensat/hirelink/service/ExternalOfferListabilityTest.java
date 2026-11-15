package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.ExternalJobOffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalOfferListabilityTest {

    private final ExternalOfferListability policy =
            new ExternalOfferListability("MAROC_EMPLOI, SAMPLE_FEED");

    @Test
    void deepAvailableNonExcludedIsListable() {
        assertTrue(policy.isListable(offer("REMOTE_OK", true, false, false, "DEEP")));
    }

    @Test
    void unknownQualityIsListable() {
        assertTrue(policy.isListable(offer("JOOBLE", true, false, false, "UNKNOWN")));
    }

    @Test
    void nullQualityIsListableForLegacyCompat() {
        assertTrue(policy.isListable(offer("REMOTE_OK", true, false, false, null)));
    }

    @Test
    void excludedSourceNeverListableEvenIfAvailableDeep() {
        assertTrue(policy.isSourceExcluded("maroc_emploi"));
        assertFalse(policy.isListable(offer("MAROC_EMPLOI", true, false, false, "DEEP")));
        assertFalse(policy.isListable(offer("sample_feed", true, false, false, "DEEP")));
    }

    @Test
    void quarantinedOrHiddenOrUnavailableRejected() {
        assertFalse(policy.isListable(offer("REMOTE_OK", false, false, false, "DEEP")));
        assertFalse(policy.isListable(offer("REMOTE_OK", true, true, false, "DEEP")));
        assertFalse(policy.isListable(offer("REMOTE_OK", true, false, true, "DEEP")));
    }

    @Test
    void genericHomeAndInvalidRejected() {
        assertFalse(policy.isListable(offer("REMOTE_OK", true, false, false, "GENERIC_HOME")));
        assertFalse(policy.isListable(offer("REMOTE_OK", true, false, false, "INVALID")));
    }

    @Test
    void nullOfferRejected() {
        assertFalse(policy.isListable(null));
    }

    private static ExternalJobOffer offer(String source, boolean available, boolean quarantined,
                                          boolean hidden, String quality) {
        ExternalJobOffer o = new ExternalJobOffer();
        o.setSource(source);
        o.setAvailable(available);
        o.setQuarantined(quarantined);
        o.setHiddenByAdmin(hidden);
        o.setUrlQuality(quality);
        o.setTitle("T");
        o.setCompany("C");
        o.setOriginUrl("https://example.com/jobs/1");
        return o;
    }
}
