package ma.ensat.hirelink.service.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repli Web optionnel (Tavily ou Serper) — uniquement quand le catalogue API est mince.
 * Clés via env uniquement. Sans clé : isReady=false, le clone reste fonctionnel.
 * Ne s'exécute pas à chaque frappe : appelé par {@link ma.ensat.hirelink.service.MultiSourceWebJobDiscovery}
 * seulement si le merge API a moins de N résultats et que la requête est non vide.
 */
@Component
public class WebSearchFallbackSource {

    private static final Pattern COMPANY_PATTERNS = Pattern.compile(
            "(?i)\\bat\\s+([A-Z][\\w.&-]{1,40})|\\bchez\\s+([A-Za-z][\\w.&-]{1,40})");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String tavilyKey;
    private final String serperKey;
    private final int thinThreshold;
    private final int maxResults;
    private final long cacheTtlSec;

    public WebSearchFallbackSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.fallback.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.fallback.tavily-key:${HIRELINK_TAVILY_API_KEY:}}") String tavilyKey,
            @Value("${hirelink.web-search.fallback.serper-key:${HIRELINK_SERPER_API_KEY:}}") String serperKey,
            @Value("${hirelink.web-search.fallback.thin-threshold:4}") int thinThreshold,
            @Value("${hirelink.web-search.fallback.max-results:8}") int maxResults,
            @Value("${hirelink.web-search.fallback.cache-ttl-sec:1800}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.tavilyKey = tavilyKey == null ? "" : tavilyKey.trim();
        this.serperKey = serperKey == null ? "" : serperKey.trim();
        this.thinThreshold = Math.max(1, thinThreshold);
        this.maxResults = Math.max(1, Math.min(15, maxResults));
        this.cacheTtlSec = Math.max(300, cacheTtlSec);
    }

    public int thinThreshold() { return thinThreshold; }

    public boolean isReady() {
        return enabled && (!tavilyKey.isBlank() || !serperKey.isBlank());
    }

    public String notReadyReason() {
        if (!enabled) return "Repli Web désactivé.";
        if (tavilyKey.isBlank() && serperKey.isBlank()) {
            return "Repli Web inactif — HIRELINK_TAVILY_API_KEY ou HIRELINK_SERPER_API_KEY absent.";
        }
        return "";
    }

    public String providerLabel() {
        if (!tavilyKey.isBlank()) return "Tavily";
        if (!serperKey.isBlank()) return "Serper";
        return "Web";
    }

    public String code() { return "WEB_FALLBACK"; }

    public List<DiscoveredJob> search(String query, String countryCode, String location, int limit) {
        if (!isReady() || query == null || query.isBlank()) return List.of();
        String q = query.trim();
        String cacheKey = "WEB_FALLBACK|" + q.toLowerCase(Locale.ROOT) + "|"
                + (countryCode == null ? "ALL" : countryCode) + "|" + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) {
            int cap = Math.min(limit, maxResults);
            return cached.size() <= cap ? cached : cached.subList(0, cap);
        }
        try {
            List<DiscoveredJob> out = !tavilyKey.isBlank()
                    ? fetchTavily(q, countryCode, location, limit)
                    : fetchSerper(q, countryCode, location, limit);
            cache.put(cacheKey, out, cacheTtlSec);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Repli Web indisponible : " + e.getMessage(), e);
        }
    }

    private List<DiscoveredJob> fetchTavily(String query, String country, String location, int limit) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("api_key", tavilyKey);
        body.put("query", buildSearchQuery(query, country, location));
        body.put("search_depth", "basic");
        body.put("include_answer", false);
        body.put("max_results", Math.min(maxResults, Math.max(limit, 5)));
        JsonNode root = mapper.readTree(WebJobHttp.postJson("https://api.tavily.com/search", mapper.writeValueAsString(body)));
        JsonNode results = root.path("results");
        return parseGenericResults(results, "url", "title", "content", country, location, limit, "Tavily", "https://tavily.com");
    }

    private List<DiscoveredJob> fetchSerper(String query, String country, String location, int limit) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("q", buildSearchQuery(query, country, location));
        body.put("num", Math.min(maxResults, Math.max(limit, 5)));
        // Serper attend la clé en header ; on l'envoie aussi via query param style body for simplicity —
        // official API uses X-API-KEY header. Extend WebJobHttp would be heavier; use URL with key denied.
        // Use a dedicated POST with header via local request here.
        String json = mapper.writeValueAsString(body);
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://google.serper.dev/search"))
                .timeout(java.time.Duration.ofSeconds(18))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-API-KEY", serperKey)
                .header("User-Agent", "HireLink/1.0 (job-discovery; educational)")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();
        java.net.http.HttpResponse<String> res = java.net.http.HttpClient.newHttpClient()
                .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " Serper");
        }
        JsonNode root = mapper.readTree(res.body());
        JsonNode results = root.path("organic");
        return parseGenericResults(results, "link", "title", "snippet", country, location, limit, "Serper", "https://serper.dev");
    }

    private List<DiscoveredJob> parseGenericResults(
            JsonNode results, String urlField, String titleField, String snippetField,
            String country, String location, int limit, String provider, String home) {
        List<DiscoveredJob> out = new ArrayList<>();
        if (results == null || !results.isArray()) return out;
        for (JsonNode n : results) {
            String title = text(n, titleField);
            String url = WebJobHttp.deepJobUrl(text(n, urlField));
            if (title == null || url == null) continue;
            if (!looksLikeJob(title, text(n, snippetField), url)) continue;
            String snippet = text(n, snippetField);
            String company = guessCompany(title, snippet);
            String locGuess = location;
            String inferred = geo.inferCountryCode(locGuess != null ? locGuess : snippet);
            DiscoveredJob job = new DiscoveredJob(
                    cleanTitle(title),
                    company,
                    locGuess,
                    locGuess,
                    inferred,
                    null,
                    null,
                    null,
                    snippet,
                    url,
                    url,
                    code(),
                    provider,
                    home);
            if (!geo.matches(job, country, location)) continue;
            out.add(job);
            if (out.size() >= Math.min(limit, maxResults)) break;
        }
        return out;
    }

    private static String buildSearchQuery(String query, String country, String location) {
        StringBuilder sb = new StringBuilder(query.trim()).append(" job OR emploi OR offre OR hiring");
        if (location != null && !location.isBlank()) sb.append(' ').append(location.trim());
        if (country != null && !country.isBlank() && !"ALL".equalsIgnoreCase(country)) {
            sb.append(' ').append(country.trim());
        }
        return sb.toString();
    }

    private static boolean looksLikeJob(String title, String snippet, String url) {
        String blob = ((title == null ? "" : title) + " " + (snippet == null ? "" : snippet) + " " + url)
                .toLowerCase(Locale.ROOT);
        return blob.contains("job") || blob.contains("emploi") || blob.contains("career")
                || blob.contains("recruit") || blob.contains("hiring") || blob.contains("stage")
                || blob.contains("internship") || blob.contains("offre") || url.contains("/jobs/")
                || url.contains("/job/") || url.contains("greenhouse") || url.contains("lever.co")
                || url.contains("ashby");
    }

    private static String cleanTitle(String title) {
        String t = title.trim();
        if (t.length() > 180) t = t.substring(0, 180);
        return t;
    }

    private static String guessCompany(String title, String snippet) {
        Matcher m = COMPANY_PATTERNS.matcher(title + " " + (snippet == null ? "" : snippet));
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            if (g != null && g.length() > 1) return g;
        }
        return "Entreprise (via Web)";
    }

    private static String text(JsonNode n, String field) {
        if (n == null || field == null) return null;
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }
}
