package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Nettoyage des lignes de description des formules : idempotent, sans vocabulaire de développement. */
class PlanCatalogCleanupTest {

    @Test
    void lesMentionsRetireesEtSimuleesDisparaissent() {
        String dirty = "3 offres actives\n[RETIRÉ Vague 2 — alias ESSENTIEL]\n[RETIRÉ Vague 2 — alias ESSENTIEL]\nPaiement simulé TTC\n"
                + "Offres actives illimitées (garde-fou technique 500)\n3 offres actives";
        String clean = PlanCatalogMigrationService.cleanFeatures(dirty);
        assertEquals("3 offres actives\nOffres actives illimitées", clean);
        assertEquals(clean, PlanCatalogMigrationService.cleanFeatures(clean));
    }

    @Test
    void uneDescriptionPropreNEstPasModifiee() {
        String ok = PlanCatalogMigrationService.PROFESSIONNEL_FEATURES;
        assertEquals(ok, PlanCatalogMigrationService.cleanFeatures(ok));
    }
}
