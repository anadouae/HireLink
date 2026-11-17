package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Attribution recherche Web : libellés + homes, sans activer le sync. */
class ExternalSourceCatalogWebAttributionTest {

    @Test
    void webSources_haveFriendlyLabelsAndHomes_withoutEnablingSync() {
        ExternalSourceCatalog catalog = new ExternalSourceCatalog(
                false, true, false, false, false, false);

        assertEquals("Remotive", catalog.labelOf("REMOTIVE"));
        assertEquals("https://remotive.com", catalog.homeOf("REMOTIVE"));
        assertEquals("Arbeitnow", catalog.labelOf("ARBEITNOW"));
        assertEquals("https://www.arbeitnow.com", catalog.homeOf("ARBEITNOW"));
        assertEquals("Remote OK", catalog.labelOf("REMOTE_OK"));
        assertEquals("https://remoteok.com", catalog.homeOf("REMOTE_OK"));
        assertEquals("We Work Remotely", catalog.labelOf("WE_WORK_REMOTELY"));
        assertEquals("https://weworkremotely.com", catalog.homeOf("WE_WORK_REMOTELY"));
        assertEquals("Himalayas", catalog.labelOf("HIMALAYAS"));
        assertEquals("https://himalayas.app", catalog.homeOf("HIMALAYAS"));
        assertEquals("Jobicy", catalog.labelOf("JOBICY"));
        assertEquals("Adzuna", catalog.labelOf("ADZUNA"));

        assertFalse(catalog.find("REMOTIVE").enabled());
        assertFalse(catalog.find("ARBEITNOW").enabled());
        assertFalse(catalog.find("REMOTE_OK").enabled());
        assertFalse(catalog.find("WE_WORK_REMOTELY").enabled());
        assertFalse(catalog.find("HIMALAYAS").enabled());
        assertFalse(catalog.find("JOBICY").enabled());
        assertTrue(catalog.enabled().stream().noneMatch(s ->
                s.code().equals("REMOTIVE") || s.code().equals("ARBEITNOW")
                        || s.code().equals("REMOTE_OK") || s.code().equals("WE_WORK_REMOTELY")
                        || s.code().equals("HIMALAYAS") || s.code().equals("JOBICY")));
    }

    @Test
    void remoteOkSyncFlag_enablesOnlyRemoteOkInEnabledList() {
        ExternalSourceCatalog catalog = new ExternalSourceCatalog(
                true, true, false, false, false, false);
        assertTrue(catalog.find("REMOTE_OK").enabled());
        assertTrue(catalog.enabled().stream().anyMatch(s -> s.code().equals("REMOTE_OK")));
        assertFalse(catalog.find("REMOTIVE").enabled());
    }
}
