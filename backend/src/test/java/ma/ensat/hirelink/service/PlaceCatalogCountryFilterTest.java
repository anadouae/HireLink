package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceCatalogCountryFilterTest {

    private final PlaceCatalogService places = new PlaceCatalogService(new TextMatcher());

    @Test
    void maFilterExcludesForeignCitiesForTaQuery() {
        List<PlaceCatalogService.Place> rows = places.searchCities("TA", "MA", 40);
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().anyMatch(p -> p.city().equalsIgnoreCase("Tanger") || p.city().startsWith("Tanger")));
        assertTrue(rows.stream().allMatch(p -> "MA".equals(p.code())));
        assertTrue(rows.stream().noneMatch(p -> "Paris".equalsIgnoreCase(p.city())));
        assertTrue(rows.stream().noneMatch(p -> p.city().isEmpty()));
    }

    @Test
    void marocLabelAcceptedAsCountry() {
        List<PlaceCatalogService.Place> rows = places.searchCities("Casa", "Maroc", 10);
        assertTrue(rows.stream().allMatch(p -> "MA".equals(p.code())));
        assertTrue(rows.stream().anyMatch(p -> p.city().equalsIgnoreCase("Casablanca")));
    }

    @Test
    void frFilterKeepsFrenchCitiesOnly() {
        List<PlaceCatalogService.Place> rows = places.searchCities("Par", "FR", 20);
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(p -> "FR".equals(p.code())));
        assertTrue(rows.stream().anyMatch(p -> p.city().equalsIgnoreCase("Paris")));
        assertTrue(rows.stream().noneMatch(p -> "MA".equals(p.code())));
    }

    @Test
    void blankCountryMeansNoCountryFilter() {
        List<PlaceCatalogService.Place> rows = places.searchCities("Par", "", 20);
        assertTrue(rows.stream().anyMatch(p -> "FR".equals(p.code())));
        // Sans filtre pays, le catalogue n'est pas limité au Maroc.
        assertTrue(rows.stream().map(PlaceCatalogService.Place::code).distinct().count() >= 1);
    }

    @Test
    void emptyQueryStillRespectsCountry() {
        List<PlaceCatalogService.Place> rows = places.searchCities("", "MA", 15);
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(p -> "MA".equals(p.code())));
    }
}
