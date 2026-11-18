package ma.ensat.hirelink.service.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.service.TextMatcher;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Remote OK — JSON public ({@code https://remoteok.com/api}), déjà utilisé dans HireLink.
 * Offres remote ; géo filtrée strictement. URL = fiche offre Remote OK (deep-link).
 */
@Component
public class RemoteOkWebJobSource implements WebJobSource {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String api;
    private final long cacheTtlSec;

    public RemoteOkWebJobSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.remoteok.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.remoteok.api:https://remoteok.com/api}") String api,
            @Value("${hirelink.web-search.remoteok.cache-ttl-sec:1800}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.api = api;
        this.cacheTtlSec = Math.max(120, cacheTtlSec);
    }

    @Override public String code() { return "REMOTE_OK"; }
    @Override public String label() { return "Remote OK"; }
    @Override public String homeUrl() { return "https://remoteok.com"; }
    @Override public String coverageNote() {
        return "Remote OK : offres remote mondiales (API publique). Géo vérifiée offre par offre.";
    }
    @Override public Set<String> countryHints() { return Set.of(); }
    @Override public boolean isReady() { return enabled; }
    @Override public String notReadyReason() { return enabled ? "" : "Remote OK désactivé pour la recherche Web."; }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!enabled) return List.of();
        String cacheKey = "REMOTE_OK|" + (query == null ? "" : query.trim().toLowerCase())
                + "|" + (countryCode == null ? "ALL" : countryCode) + "|" + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) return cached.size() <= limit ? cached : cached.subList(0, limit);
        try {
            JsonNode root = mapper.readTree(WebJobHttp.getJson(api));
            if (!root.isArray()) return List.of();
            String qFold = query == null ? "" : TextMatcher.fold(query);
            List<DiscoveredJob> out = new ArrayList<>();
            for (JsonNode n : root) {
                if (!n.hasNonNull("id") || !n.hasNonNull("position")) continue;
                String title = text(n, "position");
                String apply = WebJobHttp.deepJobUrl(text(n, "apply_url"));
                String url = WebJobHttp.deepJobUrl(text(n, "url"));
                String deep = apply != null ? apply : url;
                if (title == null || deep == null) continue;
                String company = text(n, "company");
                String loc = text(n, "location");
                if (!qFold.isEmpty()) {
                    String blob = TextMatcher.fold(title + " " + company + " " + loc + " " + joinTags(n.path("tags")));
                    if (!blob.contains(qFold) && !allWords(qFold, blob)) continue;
                }
                String inferred = geo.inferCountryCode(loc);
                DiscoveredJob job = new DiscoveredJob(
                        title,
                        company == null ? "Entreprise (via Remote OK)" : company,
                        loc,
                        loc,
                        inferred,
                        null,
                        "REMOTE",
                        joinTags(n.path("tags")),
                        null,
                        deep,
                        deep,
                        code(),
                        label(),
                        homeUrl()).withDetails(WebJobFields.date(WebJobFields.first(n, "date", "epoch")),
                        WebJobFields.amount(n.get("salary_min")), WebJobFields.amount(n.get("salary_max")), "USD");
                if (!geo.matches(job, countryCode, location)) continue;
                out.add(job);
                if (out.size() >= Math.max(limit, 20)) break;
            }
            cache.put(cacheKey, out, cacheTtlSec);
            return out.size() <= limit ? out : out.subList(0, limit);
        } catch (Exception e) {
            throw new IllegalStateException("Remote OK indisponible : " + e.getMessage(), e);
        }
    }

    private static boolean allWords(String qFold, String blob) {
        for (String w : qFold.split(" ")) {
            if (w.length() < 3) continue;
            if (!blob.contains(w)) return false;
        }
        return true;
    }

    private static String joinTags(JsonNode tags) {
        if (tags == null || !tags.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : tags) {
            String s = t.isTextual() ? t.asText() : (t.isNumber() ? t.asText() : null);
            if (s == null || s.isBlank()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
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
