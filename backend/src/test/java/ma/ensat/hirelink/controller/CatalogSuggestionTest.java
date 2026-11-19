package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.service.PlaceCatalogService;
import ma.ensat.hirelink.service.TextMatcher;
import ma.ensat.hirelink.service.CompanySuggestService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CatalogSuggestionTest {

    private final CatalogSuggestController controller = new CatalogSuggestController(
            new TextMatcher(),
            new PlaceCatalogService(new TextMatcher()),
            mock(CompanySuggestService.class)
    );

    @Test
    void vingtCinqCasDeRechercheSontCouverts() {
        assertSuggestion("place", "tetouan", "Tétouan");
        assertSuggestion("city", "tetwan", "Tétouan");
        assertSuggestion("country", "maroc", "Maroc");
        assertSuggestion("nationality", "marocaine", "Marocaine");
        assertSuggestion("language", "anglai", "Anglais");
        assertSuggestion("language", "francais", "Français");
        assertSuggestion("skill", "docekr", "Docker");
        assertSuggestion("skill", "js", "JavaScript");
        assertSuggestion("skill", "ia", "Intelligence artificielle");
        assertSuggestion("skill", "devops", "DevOps");
        assertSuggestion("job", "dev", "Développeur");
        assertSuggestion("job", "developpeur java", "Développeur Java");
        assertSuggestion("school", "ensa tetwan", "ENSA Tétouan");
        assertSuggestion("school", "ui r", "UIR");
        assertSuggestion("domain", "ingenierie", "Ingénierie");
        assertSuggestion("domain", "info", "Informatique");
        assertSuggestion("contract", "cdi", "CDI");
        assertSuggestion("mode", "remote", "Télétravail");
        assertSuggestion("place", "paris", "Paris");
        assertSuggestion("place", "londres", "Londres");
        assertSuggestion("country", "france", "France");
        assertSuggestion("nationality", "francaise", "Française");
        assertSuggestion("language", "arabe", "Arabe");
        assertSuggestion("skill", "postman", "Postman");
        assertSuggestion("skill", "spring boot", "Spring Boot");
    }

    @Test
    void champVideEtResultatsLimites() {
        List<CatalogSuggestController.Suggestion> empty = controller.suggest("skill", "", null, null);
        assertFalse(empty.isEmpty());
        assertTrue(empty.size() <= 8);
    }

    @Test
    void mardiMarcheMariageNeSontJamaisDesLieux() {
        for (String query : List.of("mardi", "marche", "mariage")) {
            List<CatalogSuggestController.Suggestion> rows = controller.suggest("place", query, null, null);
            assertTrue(rows.stream().noneMatch(row ->
                    List.of("mardi", "marche", "mariage").contains(row.label().toLowerCase())));
        }
    }

    private void assertSuggestion(String kind, String query, String expectedFragment) {
        List<CatalogSuggestController.Suggestion> rows = controller.suggest(kind, query, null, null);
        assertTrue(rows.stream().anyMatch(row ->
                        row.label().toLowerCase().contains(expectedFragment.toLowerCase())
                                || expectedFragment.toLowerCase().contains(row.label().toLowerCase())),
                () -> kind + " / " + query + " n'a pas donné " + expectedFragment + " : " + rows);
    }
}
