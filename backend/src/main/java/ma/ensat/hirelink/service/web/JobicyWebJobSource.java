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
 * Jobicy — API publique remote ({@code https://jobicy.com/api/v2/remote-jobs}).
 * Attribution : source Jobicy + deep-link.
 */
@Component
public class JobicyWebJobSource implements WebJobSource {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String apiBase;
    private final long cacheTtlSec;

    public JobicyWebJobSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.jobicy.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.jobicy.api:https://jobicy.com/api/v2/remote-jobs}") String apiBase,
            @Value("${hirelink.web-search.jobicy.cache-ttl-sec:3600}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.cacheTtlSec = Math.max(300, cacheTtlSec);
    }

    @Override public String code() { return "JOBICY"; }
    @Override public String label() { return "Jobicy"; }
    @Override public String homeUrl() { return "https://jobicy.com"; }
    @Override public String coverageNote() {
        return "Jobicy : offres remote (API publique). Attribution Jobicy requise.";
    }
    @Override public Set<String> countryHints() { return Set.of(); }
    @Override public boolean isReady() { return enabled; }
    @Override public String notReadyReason() {
        return enabled ? "" : "Jobicy désactivé (hirelink.web-search.jobicy.enabled=false).";
    }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!enabled) return List.of();
        String q = query == null ? "" : query.trim();
        String cacheKey = "JOBICY|" + q.toLowerCase(Locale.ROOT) + "|"
                + (countryCode == null ? "ALL" : countryCode) + "|" + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.size() <= limit ? cached : cached.subList(0, limit);
        }
        try {
            StringBuilder url = new StringBuilder(apiBase)
                    .append("?count=").append(Math.min(50, Math.max(limit * 3, 20)));
            if (!q.isBlank()) {
                url.append("&tag=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
            }
            JsonNode root = mapper.readTree(WebJobHttp.getJson(url.toString()));
            JsonNode jobs = root.path("jobs");
            if (!jobs.isArray()) return List.of();
            List<DiscoveredJob> out = new ArrayList<>();
            for (JsonNode n : jobs) {
                String title = text(n, "jobTitle");
                if (title == null) title = text(n, "title");
                String deep = WebJobHttp.deepJobUrl(text(n, "url"));
                if (title == null || deep == null) continue;
                if (!q.isBlank() && !titleAndBlobMatch(q, title, n)) continue;
                String company = text(n, "companyName");
                String loc = text(n, "jobGeo");
                String inferred = geo.inferCountryCode(loc);
                String skills = joinTags(n.path("jobIndustry"));
                if (skills == null) skills = joinTags(n.path("jobType"));
                Integer minSal = WebJobFields.amount(WebJobFields.first(n, "salaryMin", "annualSalaryMin"));
                Integer maxSal = WebJobFields.amount(WebJobFields.first(n, "salaryMax", "annualSalaryMax"));
                String period = WebJobFields.period(text(n, "salaryPeriod"));
                // annualSalaryMin / annualSalaryMax : le nom du champ dit explicitement « annuel ».
                if (period == null && (n.hasNonNull("annualSalaryMin") || n.hasNonNull("annualSalaryMax"))
                        && (minSal != null || maxSal != null)) {
                    period = "YEAR";
                }
                DiscoveredJob job = new DiscoveredJob(
                        title,
                        company == null ? "Entreprise (via Jobicy)" : company,
                        loc,
                        loc,
                        inferred,
                        firstArrayText(n.path("jobType")),
                        "REMOTE",
                        skills,
                        text(n, "jobExcerpt"),
                        deep,
                        deep,
                        code(),
                        label(),
                        homeUrl()).withDetails(WebJobFields.date(WebJobFields.first(n, "pubDate", "publishedAt")),
                        minSal, maxSal, text(n, "salaryCurrency"), period);
                if (!geo.matches(job, countryCode, location)) continue;
                out.add(job);
                if (out.size() >= Math.max(limit, 20)) break;
            }
            cache.put(cacheKey, out, cacheTtlSec);
            return out.size() <= limit ? out : out.subList(0, limit);
        } catch (Exception e) {
            throw new IllegalStateException("Jobicy indisponible : " + e.getMessage(), e);
        }
    }

    private static boolean titleAndBlobMatch(String q, String title, JsonNode n) {
        String blob = (title + " " + text(n, "companyName") + " " + text(n, "jobExcerpt"))
                .toLowerCase(Locale.ROOT);
        for (String token : q.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (token.length() < 2) continue;
            if (!blob.contains(token)) return false;
        }
        return true;
    }

    private static String joinTags(JsonNode tags) {
        if (tags == null || !tags.isArray()) {
            if (tags != null && tags.isTextual()) return tags.asText();
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : tags) {
            if (!t.isTextual()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t.asText());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String firstArrayText(JsonNode tags) {
        if (tags == null) return null;
        if (tags.isTextual()) return tags.asText();
        if (tags.isArray() && tags.size() > 0 && tags.get(0).isTextual()) return tags.get(0).asText();
        return null;
    }

    private static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }
}
