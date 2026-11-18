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
import java.util.Set;

/**
 * Remotive — API publique sans clé ({@code https://remotive.com/api/remote-jobs}).
 * Couverture : offres remote internationales. URL = fiche Remotive de l'annonce (pas la home).
 * Attribution : source Remotive.
 */
@Component
public class RemotiveWebJobSource implements WebJobSource {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String apiBase;
    private final long cacheTtlSec;

    public RemotiveWebJobSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.remotive.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.remotive.api:https://remotive.com/api/remote-jobs}") String apiBase,
            @Value("${hirelink.web-search.remotive.cache-ttl-sec:21600}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.cacheTtlSec = Math.max(300, cacheTtlSec); // min 5 min — quota Remotive
    }

    @Override public String code() { return "REMOTIVE"; }
    @Override public String label() { return "Remotive"; }
    @Override public String homeUrl() { return "https://remotive.com"; }
    @Override public String coverageNote() {
        return "Remotive : offres remote internationales (API publique). Pas une couverture exhaustive du Web.";
    }
    @Override public Set<String> countryHints() { return Set.of(); }
    @Override public boolean isReady() { return enabled; }
    @Override public String notReadyReason() { return enabled ? "" : "Remotive désactivé (hirelink.web-search.remotive.enabled=false)."; }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!enabled) return List.of();
        String cacheKey = "REMOTIVE|" + (query == null ? "" : query.trim().toLowerCase())
                + "|" + (countryCode == null ? "ALL" : countryCode) + "|" + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.size() <= limit ? cached : cached.subList(0, limit);
        }
        try {
            StringBuilder url = new StringBuilder(apiBase).append("?limit=").append(Math.min(50, Math.max(limit * 3, 20)));
            if (query != null && !query.isBlank()) {
                url.append("&search=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
            }
            JsonNode root = mapper.readTree(WebJobHttp.getJson(url.toString()));
            JsonNode jobs = root.path("jobs");
            if (!jobs.isArray()) return List.of();
            List<DiscoveredJob> out = new ArrayList<>();
            for (JsonNode n : jobs) {
                String title = text(n, "title");
                String deep = WebJobHttp.deepJobUrl(text(n, "url"));
                if (title == null || deep == null) continue;
                String loc = text(n, "candidate_required_location");
                String inferred = geo.inferCountryCode(loc);
                WebJobFields.ParsedPay pay = WebJobFields.pay(text(n, "salary"));
                DiscoveredJob job = new DiscoveredJob(
                        title,
                        text(n, "company_name") == null ? "Entreprise (via Remotive)" : text(n, "company_name"),
                        loc,
                        loc,
                        inferred,
                        text(n, "job_type"),
                        "REMOTE",
                        joinTags(n.path("tags")),
                        null,
                        deep,
                        deep,
                        code(),
                        label(),
                        homeUrl()).withDetails(WebJobFields.date(n.get("publication_date")),
                        pay.min(), pay.max(), pay.currency(), pay.period());
                if (!geo.matches(job, countryCode, location)) continue;
                out.add(job);
                if (out.size() >= Math.max(limit, 20)) break;
            }
            cache.put(cacheKey, out, cacheTtlSec);
            return out.size() <= limit ? out : out.subList(0, limit);
        } catch (Exception e) {
            throw new IllegalStateException("Remotive indisponible : " + e.getMessage(), e);
        }
    }

    private static String joinTags(JsonNode tags) {
        if (tags == null || !tags.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : tags) {
            if (!t.isTextual()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t.asText());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }
}
