package ma.ensat.hirelink.service;

import ma.ensat.hirelink.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanySuggestServiceTest {

    @Test
    void tanPrefereDebutDeMotAuxSousChaines() {
        List<String> ranked = CompanySuggestService.rankNames("tan", List.of(
                "OCP Group",
                "Tanger Med",
                "Tanmia",
                "Stanlog",
                "Maroc Telecom"
        ), 8);
        assertEquals(List.of("Tanger Med", "Tanmia", "Stanlog"), ranked);
        assertTrue(ranked.indexOf("Tanger Med") < ranked.indexOf("Stanlog"));
        assertTrue(ranked.indexOf("Tanmia") < ranked.indexOf("Stanlog"));
    }

    @Test
    void moinsDeDeuxCaracteresVide() {
        assertTrue(CompanySuggestService.rankNames("t", List.of("Tanger Med"), 8).isEmpty());
    }

    @Test
    void limiteHuitEtDedup() {
        List<String> names = List.of(
                "Tanger Med", "tanger med", "Tanmia", "Tan Tan", "Tany",
                "Tangram", "Tannerie Atlas", "Tanit", "Tanger Free Zone", "Stanlog"
        );
        List<String> ranked = CompanySuggestService.rankNames("tan", names, 8);
        assertEquals(8, ranked.size());
        assertEquals(1, ranked.stream().filter(n -> n.equalsIgnoreCase("tanger med")).count());
        assertTrue(ranked.contains("Tanger Med"));
        assertTrue(ranked.contains("Tanmia"));
        assertTrue(!ranked.contains("Stanlog") || ranked.indexOf("Tanger Med") < ranked.indexOf("Stanlog"));
    }

    @Test
    void fusionneLesSourcesEtExclutLesEntreprisesDesactivees() {
        OrganizationRepository orgs = mock(OrganizationRepository.class);
        when(orgs.findActiveNames()).thenReturn(List.of("Tanger Med", "Atlas Digital", "TANGER MED"));
        when(orgs.findInactiveNames()).thenReturn(List.of("Tanit Conseil"));

        // Contexte = entreprises des résultats affichés (offres Web comprises).
        List<String> out = new CompanySuggestService(orgs)
                .suggest("tan", "Tanmia|Tangram Labs|Entreprise non précisée|Société Tané|Stanlog|Tanit Conseil");

        assertEquals(List.of("Tanger Med", "Tangram Labs", "Tanmia", "Société Tané", "Stanlog"), out);
        assertTrue(out.stream().noneMatch(n -> n.toLowerCase().contains("tanit")), "entreprise désactivée jamais proposée");
    }

    @Test
    void unSeulCaractereNInterrogeRien() {
        OrganizationRepository orgs = mock(OrganizationRepository.class);
        assertTrue(new CompanySuggestService(orgs).suggest("t", null).isEmpty());
        verify(orgs, never()).findActiveNames();
    }
}
