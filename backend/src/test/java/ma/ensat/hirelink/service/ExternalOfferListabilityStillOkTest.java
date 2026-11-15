package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.ExternalJobOffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Non-régression B0 listabilité (ne dépend pas du JAR 8081). */
class ExternalOfferListabilityStillOkTest {
    @Test
    void marocExcluded() {
        ExternalOfferListability p = new ExternalOfferListability("MAROC_EMPLOI");
        ExternalJobOffer o = new ExternalJobOffer();
        o.setSource("MAROC_EMPLOI");
        o.setAvailable(true);
        o.setUrlQuality("DEEP");
        assertFalse(p.isListable(o));
        o.setSource("REMOTE_OK");
        assertTrue(p.isListable(o));
    }
}
