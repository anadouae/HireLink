package ma.ensat.hirelink.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractTypeTest {

    @Test
    void lesPreferencesCouvrentLesOffresEtLesSituationsDuProfil() {
        assertTrue(ContractType.PREFERENCES.containsAll(java.util.List.of("CDI", "Stage", "PFE", "Premier emploi", "Job étudiant", "Bénévolat")));
    }

    @Test
    void nettoyageGardeLesValeursConnuesSansDoublon() {
        assertEquals("Premier emploi, CDI", ContractType.cleanPreferences(" premier emploi, CDI , cdi, Inconnu "));
        assertEquals("", ContractType.cleanPreferences(null));
    }
}
