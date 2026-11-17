package ma.ensat.hirelink.service.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Himalayas — API publique remote ({@code https://himalayas.app/jobs/api}).
 * Attribution : source Himalayas + lien deep vers l'annonce.
 */
@Component
public class HimalayasWebJobSource implements WebJobSource {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String apiBase;
    private final long cacheTtlSec;

    public HimalayasWebJobSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.himalayas.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.himalayas.api:https://himalayas.app/jobs/api}") String apiBase,
            @Value("${hirelink.web-search.himalayas.cache-ttl-sec:3600}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.cacheTtlSec = Math.max(300, cacheTtlSec);
    }

    @Override public String code() { return "HIMALAYAS"; }
    @Override public String label() { return "Himalayas"; }
    @Override public String homeUrl() { return "https://himalayas.app"; }
    @Override public String coverageNote() {
        return "Himalayas : offres remote internationales (API publique). Attribution Himalayas requise.";
    }
    @Override public Set<String> countryHints() { return Set.of(); }
    @Override public boolean isReady() { return enabled; }
    @Override public String notReadyReason() {
        return enabled ? "" : "Himalayas désactivé (hirelink.web-search.himalayas.enabled=false).";
    }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!enabled) return List.of();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String cacheKey = "HIMALAYAS|" + q + "|" + (countryCode == null ? "ALL" : countryCode)
                + "|" + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.size() <= limit ? cached : cached.subList(0, limit);
        }
        try {
            int want = Math.min(50, Math.max(limit * 3, 20));
            JsonNode root = mapper.readTree(WebJobHttp.getJson(apiBase + "?limit=" + want));
            JsonNode jobs = root.path("data");
            if (!jobs.isArray()) jobs = root.path("jobs");
            if (!jobs.isArray()) return List.of();
            List<DiscoveredJob> out = new ArrayList<>();
            for (JsonNode n : jobs) {
                String title = firstText(n, "title", "jobTitle", "name");
                String deep = WebJobHttp.deepJobUrl(firstText(n, "applicationLink", "url", "guid", "permalink"));
                if (title == null || deep == null) continue;
                if (!q.isEmpty() && !matchesQuery(q, title, n)) continue;
                String company = firstText(n, "companyName", "company", "organization");
                String loc = firstText(n, "location", "locations", "candidateRequiredLocation");
                if (loc == null && n.path("locationRestrictions").isArray() && n.path("locationRestrictions").size() > 0) {
                    loc = n.path("locationRestrictions").get(0).asText();
                }
                String inferred = geo.inferCountryCode(loc);
                String skills = joinArray(n.path("categories"));
                if (skills == null) skills = joinArray(n.path("skills"));
                DiscoveredJob job = new DiscoveredJob(
                        title,
                        company == null ? "Entreprise (via Himalayas)" : company,
                        loc,
                        loc,
                        inferred,
                        firstText(n, "employmentType", "jobType", "type"),
                        "REMOTE",
                        skills,
                        null,
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
            throw new IllegalStateException("Himalayas indisponible : " + e.getMessage(), e);
        }
    }

    private static boolean matchesQuery(String q, String title, JsonNode n) {
        String blob = (title + " " + firstText(n, "companyName", "company", "")
                + " " + joinArray(n.path("categories"))).toLowerCase(Locale.ROOT);
        for (String token : q.split("\\s+")) {
            if (token.length() < 2) continue;
            if (!blob.contains(token)) return false;
        }
        return true;
    }

    private static String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : arr) {
            String s = t.isTextual() ? t.asText() : firstText(t, "name", "title", "label");
            if (s == null || s.isBlank()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String firstText(JsonNode n, String... fields) {
        if (n == null) return null;
        for (String f : fields) {
            if (f == null || f.isEmpty()) continue;
            JsonNode v = n.get(f);
            if (v == null || v.isNull()) continue;
            if (v.isArray() && v.size() > 0) {
                String s = v.get(0).asText();
                if (s != null && !s.isBlank()) return s.trim();
                continue;
            }
            String s = v.asText();
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }
}
