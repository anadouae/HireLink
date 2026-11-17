package ma.ensat.hirelink.service.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Adzuna — optionnel via {@code HIRELINK_ADZUNA_APP_ID} / {@code HIRELINK_ADZUNA_APP_KEY}.
 * Sans clés : source non prête (clone local OK, pas d'appel). Attribution Adzuna obligatoire.
 */
@Component
public class AdzunaWebJobSource implements WebJobSource {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String appId;
    private final String appKey;
    private final String apiBase;
    private final String defaultCountry;
    private final long cacheTtlSec;

    public AdzunaWebJobSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.adzuna.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.adzuna.app-id:${HIRELINK_ADZUNA_APP_ID:}}") String appId,
            @Value("${hirelink.web-search.adzuna.app-key:${HIRELINK_ADZUNA_APP_KEY:}}") String appKey,
            @Value("${hirelink.web-search.adzuna.api:https://api.adzuna.com/v1/api/jobs}") String apiBase,
            @Value("${hirelink.web-search.adzuna.default-country:fr}") String defaultCountry,
            @Value("${hirelink.web-search.adzuna.cache-ttl-sec:1800}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.appId = appId == null ? "" : appId.trim();
        this.appKey = appKey == null ? "" : appKey.trim();
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.defaultCountry = defaultCountry == null || defaultCountry.isBlank() ? "fr" : defaultCountry.toLowerCase(Locale.ROOT);
        this.cacheTtlSec = Math.max(300, cacheTtlSec);
    }

    @Override public String code() { return "ADZUNA"; }
    @Override public String label() { return "Adzuna"; }
    @Override public String homeUrl() { return "https://www.adzuna.com"; }
    @Override public String coverageNote() {
        return "Adzuna : agrégateur multi-pays (clé requise). Attribution Adzuna + lien vers l'annonce.";
    }
    @Override public Set<String> countryHints() {
        return Set.of("FR", "GB", "DE", "US", "CA", "AU", "NL", "BE", "CH", "ES", "IT", "AT", "PL", "IN", "SG", "NZ", "ZA", "BR", "MX");
    }

    @Override
    public boolean isReady() {
        return enabled && !appId.isBlank() && !appKey.isBlank();
    }

    @Override
    public String notReadyReason() {
        if (!enabled) return "Adzuna désactivé (hirelink.web-search.adzuna.enabled=false).";
        if (appId.isBlank() || appKey.isBlank()) {
            return "Adzuna inactif — définissez HIRELINK_ADZUNA_APP_ID et HIRELINK_ADZUNA_APP_KEY (sinon ignoré).";
        }
        return "";
    }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!isReady()) return List.of();
        String country = mapCountry(countryCode);
        String q = query == null ? "" : query.trim();
        String cacheKey = "ADZUNA|" + country + "|" + q.toLowerCase(Locale.ROOT) + "|"
                + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.size() <= limit ? cached : cached.subList(0, limit);
        }
        try {
            StringBuilder url = new StringBuilder(apiBase).append('/').append(country).append("/search/1")
                    .append("?app_id=").append(URLEncoder.encode(appId, StandardCharsets.UTF_8))
                    .append("&app_key=").append(URLEncoder.encode(appKey, StandardCharsets.UTF_8))
                    .append("&results_per_page=").append(Math.min(50, Math.max(limit * 2, 10)));
            if (!q.isBlank()) url.append("&what=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
            if (location != null && !location.isBlank()) {
                url.append("&where=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
            }
            JsonNode root = mapper.readTree(WebJobHttp.getJson(url.toString()));
            JsonNode results = root.path("results");
            if (!results.isArray()) return List.of();
            List<DiscoveredJob> out = new ArrayList<>();
            for (JsonNode n : results) {
                String title = text(n, "title");
                String deep = WebJobHttp.deepJobUrl(text(n, "redirect_url"));
                if (deep == null) deep = WebJobHttp.deepJobUrl(text(n, "adref"));
                if (title == null || deep == null) continue;
                String company = n.path("company").path("display_name").asText(null);
                String loc = n.path("location").path("display_name").asText(null);
                String inferred = geo.inferCountryCode(loc);
                if (inferred == null) inferred = country.toUpperCase(Locale.ROOT);
                DiscoveredJob job = new DiscoveredJob(
                        title,
                        company == null || company.isBlank() ? "Entreprise (via Adzuna)" : company,
                        loc,
                        loc,
                        inferred,
                        n.path("contract_type").asText(null),
                        null,
                        n.path("category").path("label").asText(null),
                        text(n, "description"),
                        deep,
                        deep,
                        code(),
                        label(),
                        homeUrl());
                if (!geo.matches(job, countryCode, location)) continue;
                out.add(job);
                if (out.size() >= Math.max(limit, 20)) break;
            }
            cache.put(cacheKey, out, cacheTtlSec);
            return out.size() <= limit ? out : out.subList(0, limit);
        } catch (Exception e) {
            throw new IllegalStateException("Adzuna indisponible : " + e.getMessage(), e);
        }
    }

    private String mapCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank() || "ALL".equalsIgnoreCase(countryCode)) {
            return defaultCountry;
        }
        String c = countryCode.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "gb", "uk" -> "gb";
            case "us", "usa" -> "us";
            default -> c.length() == 2 ? c : defaultCountry;
        };
    }

    private static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }
}
