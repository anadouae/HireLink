package ma.ensat.hirelink.service.external;

import ma.ensat.hirelink.service.ParsedExternalJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Connecteur Jooble MA — prepare, desactive par defaut.
 * Aucune cle inventee : sans cle + enabled=false, aucun appel reseau.
 */
@Component
public class JoobleJobConnector implements ExternalJobConnector {

    private final JoobleHttpClient http;
    private final JoobleJobParser parser = new JoobleJobParser();
    private final boolean enabled;
    private final String apiKey;
    private final String apiBase;
    private final String defaultKeywords;
    private final String defaultLocation;
    private final int maxPages;

    public JoobleJobConnector(
            JoobleHttpClient http,
            @Value("${hirelink.external-jobs.jooble.enabled:false}") boolean enabled,
            @Value("${hirelink.external-jobs.jooble.api-key:}") String apiKey,
            @Value("${hirelink.external-jobs.jooble.api-base:https://ma.jooble.org/api}") String apiBase,
            @Value("${hirelink.external-jobs.jooble.keywords:}") String defaultKeywords,
            @Value("${hirelink.external-jobs.jooble.location:Maroc}") String defaultLocation,
            @Value("${hirelink.external-jobs.jooble.max-pages:1}") int maxPages) {
        this.http = http;
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiBase = trimSlash(apiBase);
        this.defaultKeywords = defaultKeywords == null ? "" : defaultKeywords.trim();
        this.defaultLocation = defaultLocation == null ? "Maroc" : defaultLocation.trim();
        this.maxPages = Math.max(1, Math.min(5, maxPages));
    }

    @Override
    public String sourceCode() { return JoobleJobParser.SOURCE; }

    @Override
    public String label() { return "Jooble Maroc"; }

    @Override
    public String homeUrl() { return "https://ma.jooble.org"; }

    @Override
    public boolean providerEnabled() { return enabled && hasKey(); }

    public boolean hasKey() { return !apiKey.isBlank(); }

    public boolean flagEnabled() { return enabled; }

    @Override
    public List<ParsedExternalJob> fetchNormalized() {
        return fetchPages(defaultKeywords, defaultLocation);
    }

    @Override
    public List<ParsedExternalJob> fetchNormalized(String boardKey) {
        // boardKey ignore pour Jooble (pas de boards) — reuse keywords optionnels
        String kw = boardKey == null || boardKey.isBlank() ? defaultKeywords : boardKey.trim();
        return fetchPages(kw, defaultLocation);
    }

    /** Point d'entree testable avec body injecte (fixtures). */
    public List<ParsedExternalJob> parseFixture(String body) {
        return parser.parse(body);
    }

    /**
     * Recherche live pour {@code WebJobDiscovery} : exige une clé API, ignore le flag d'import sync.
     * Ne persiste rien — l'appelant mappe en résultats éphémères.
     */
    public List<ParsedExternalJob> searchLive(String keywords, String location, int pages, int resultsOnPage) {
        if (!hasKey()) {
            throw new IllegalStateException("Cle Jooble absente : aucune requete n'a ete envoyee");
        }
        String loc = location == null || location.isBlank() ? defaultLocation : location.trim();
        String kw = keywords == null ? "" : keywords.trim();
        if (kw.isBlank()) {
            throw new IllegalArgumentException("Mots-cles Jooble requis");
        }
        int pageCap = Math.max(1, Math.min(5, pages <= 0 ? 1 : pages));
        int perPage = Math.max(1, Math.min(50, resultsOnPage <= 0 ? 20 : resultsOnPage));
        java.util.ArrayList<ParsedExternalJob> all = new java.util.ArrayList<>();
        for (int page = 1; page <= pageCap; page++) {
            String url = apiBase + "/" + apiKey;
            String body = "{\"keywords\":\"" + esc(kw) + "\",\"location\":\"" + esc(loc)
                    + "\",\"page\":\"" + page + "\",\"ResultOnPage\":" + perPage + ",\"searchMode\":1}";
            String json = http.postJson(url, body);
            List<ParsedExternalJob> batch = parser.parse(json);
            all.addAll(batch);
            if (batch.isEmpty()) break;
        }
        return all;
    }

    private List<ParsedExternalJob> fetchPages(String keywords, String location) {
        if (!enabled) {
            throw new IllegalStateException("Jooble est desactive (hirelink.external-jobs.jooble.enabled=false)");
        }
        return searchLive(keywords, location, maxPages, 20);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) return "https://ma.jooble.org/api";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}