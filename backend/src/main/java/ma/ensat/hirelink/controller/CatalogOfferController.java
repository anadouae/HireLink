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
            @RequestParam(required = false) String where,
            @RequestParam(required = false, defaultValue = "ALL") String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String contract,
            @RequestParam(required = false) String workMode,
            @RequestParam(required = false, defaultValue = "all") String origin,
            @RequestParam(required = false) Integer sinceDays,
            @RequestParam(required = false) Integer minSalary,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String company,
            @RequestParam(required = false, defaultValue = "false") boolean forYou,
            // Seuil explicite conservé pour l'API (plus de filtre « Compatibilité min. » dans l'écran candidat).
            @RequestParam(required = false, defaultValue = "0") Double minMatch,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false, defaultValue = "false") boolean facets,
            // web=false : offres HireLink tout de suite ; les offres web arrivent ensuite via /catalog/web-offers.
            @RequestParam(required = false, defaultValue = "true") boolean web,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        String countryFilter = countryFilter(country);
        String workModeFilter = normalizeWorkMode(workMode);
        // « Où » (champ unique ville / pays) remplace Pays + Ville ; plus de filtre Source dans la requête.
        CatalogOfferService.CatalogPage result = catalog.search(
                user, q, where, countryFilter, city, contract, workModeFilter, null, origin, sinceDays,
                minSalary, experience, sector, company, forYou, minMatch, sort, facets, !web, page, size);
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
        // true : les offres web de cette recherche sont à demander à /catalog/web-offers (affichage progressif).
        body.put("webDeferred", "DEFERRED".equals(result.webSearchStatus()));
        if (result.facets() != null) {
            body.put("facets", result.facets());
            body.put("salaryAvailable", result.facets().get("salaryAvailable"));
        }
        body.put("searchNote", SEARCH_NOTE);
        return body;
    }

    static final String SEARCH_NOTE = "Offres HireLink : publiées par les recruteurs, candidature dans HireLink. "
            + "Offres web : cherchées en direct sur des sites d’emploi publics au moment de votre recherche ; "
            + "rien n’est stocké et la candidature se fait sur le site d’origine.";

    /**
     * Offres web de la recherche (mêmes paramètres que /offers, sans pagination), chargées après les offres
     * HireLink. Compteurs {@code facets} à additionner à ceux de /offers. Une source lente ou en panne est
     * simplement absente de la réponse.
     */
    @GetMapping("/web-offers")
    public Map<String, Object> webOffers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String where,
            @RequestParam(required = false, defaultValue = "ALL") String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String contract,
            @RequestParam(required = false) String workMode,
            @RequestParam(required = false, defaultValue = "all") String origin,
            @RequestParam(required = false) Integer sinceDays,
            @RequestParam(required = false) Integer minSalary,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String company,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false, defaultValue = "false") boolean facets) {
        CatalogOfferService.WebOffersPage result = catalog.searchWebOffers(
                q, where, countryFilter(country), city, contract, normalizeWorkMode(workMode), origin, sinceDays,
                minSalary, experience, sector, company, sort, facets);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.items());
        body.put("totalElements", result.total());
        body.put("webSearchAvailable", result.webSearchAvailable());
        body.put("webSearchStatus", result.webSearchStatus());
        body.put("webSearchMessage", result.webSearchMessage());
        body.put("webSearchCoverage", result.webSearchCoverage());
        body.put("webSearchSources", result.webSearchSources());
        if (result.facets() != null) body.put("facets", result.facets());
        return body;
    }

    /** country=* ou ALL => pas de filtre pays. */
    private static String countryFilter(String country) {
        if (country != null && (country.isBlank() || "*".equals(country) || "ALL".equalsIgnoreCase(country)
                || "TOUS".equalsIgnoreCase(country))) {
            return null;
        }
        return country;
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