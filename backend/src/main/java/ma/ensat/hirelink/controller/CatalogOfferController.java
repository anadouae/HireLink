package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.CatalogOfferService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogOfferController {

    private final CatalogOfferService catalog;

    public CatalogOfferController(CatalogOfferService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/offers")
    public Map<String, Object> offers(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "ALL") String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String contract,
            @RequestParam(required = false) String workMode,
            @RequestParam(required = false) String source,
            @RequestParam(required = false, defaultValue = "all") String origin,
            @RequestParam(required = false) Integer sinceDays,
            @RequestParam(required = false) Integer minSalary,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String company,
            @RequestParam(required = false, defaultValue = "false") boolean forYou,
            @RequestParam(required = false, defaultValue = "0") Double minMatch,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        // country=* ou ALL => pas de filtre pays
        String countryFilter = country;
        if (country != null && (country.isBlank() || "*".equals(country) || "ALL".equalsIgnoreCase(country)
                || "TOUS".equalsIgnoreCase(country))) {
            countryFilter = null;
        }
        String workModeFilter = normalizeWorkMode(workMode);
        CatalogOfferService.CatalogPage result = catalog.search(
                user, q, countryFilter, city, contract, workModeFilter, source, origin, sinceDays,
                minSalary, experience, sector, company, forYou, minMatch, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", result.page());
        body.put("size", result.size());
        body.put("totalElements", result.totalElements());
        body.put("totalPages", result.totalPages());
        body.put("items", result.items());
        body.put("defaultCountry", "MA");
        body.put("forYouTruncated", result.forYouTruncated());
        body.put("forYouScanned", result.forYouScanned());
        body.put("forYouScanCap", result.forYouScanCap());
        body.put("rankedByRelevance", result.rankedByRelevance());
        body.put("webSearchAvailable", result.webSearchAvailable());
        body.put("webSearchStatus", result.webSearchStatus());
        body.put("webSearchMessage", result.webSearchMessage());
        body.put("webSearchCoverage", result.webSearchCoverage());
        body.put("webSearchSources", result.webSearchSources());
        // Catalogue stocké = cache indexé rapide ; vérité interne = job_offers. Web = éphémère live.
        body.put("catalogCacheAt", java.time.Instant.now().toString());
        body.put("catalogCacheNote",
                "Les offres indexées sont un cache de recherche (fraîcheur ci-dessus). Les offres HireLink restent en base. Les résultats Web sont live et non persistés.");
        return body;
    }

    /** Reconstruit la projection (admin). Utile apres import isole. */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public Map<String, Object> reindex() {
        int n = catalog.rebuildIndex();
        return Map.of("indexed", n);
    }

    private static String normalizeWorkMode(String workMode) {
        if (workMode == null || workMode.isBlank()) return null;
        String k = workMode.trim().toUpperCase(java.util.Locale.ROOT);
        k = k.replace('\u00C9', 'E').replace('\u00E9', 'E');
        return switch (k) {
            case "REMOTE", "TELETRAVAIL", "WFH" -> "REMOTE";
            case "HYBRID", "HYBRIDE" -> "HYBRID";
            case "ONSITE", "PRESENTIEL", "SUR SITE", "SUR_SITE" -> "ONSITE";
            default -> k;
        };
    }
}