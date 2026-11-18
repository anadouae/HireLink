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
 * Arbeitnow — API publique sans clé (DE/EU + UK).
 * ToS : lien retour vers Arbeitnow exigé (sourceLabel / sourceHome).
 * Couverture limitée Europe — signalée ; hors zone → 0 résultat (pas de mensonge géo).
 */
@Component
public class ArbeitnowWebJobSource implements WebJobSource {

    private static final Set<String> ZONE = Set.of(
            "DE", "GB", "FR", "CH", "NL", "AT", "BE", "IE", "ES", "IT", "PL", "SE", "NO", "DK", "GR", "EE", "LU");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String apiEu;
    private final String apiUk;
    private final long cacheTtlSec;

    public ArbeitnowWebJobSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.arbeitnow.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.arbeitnow.api-eu:https://www.arbeitnow.com/api/job-board-api}") String apiEu,
            @Value("${hirelink.web-search.arbeitnow.api-uk:https://www.arbeitnow.co.uk/api/job-board-api}") String apiUk,
            @Value("${hirelink.web-search.arbeitnow.cache-ttl-sec:1800}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.apiEu = apiEu;
        this.apiUk = apiUk;
        this.cacheTtlSec = Math.max(120, cacheTtlSec);
    }

    @Override public String code() { return "ARBEITNOW"; }
    @Override public String label() { return "Arbeitnow"; }
    @Override public String homeUrl() { return "https://www.arbeitnow.com"; }
    @Override public String coverageNote() {
        return "Arbeitnow : Europe (surtout DE/FR/NL/CH/UK via ATS). Attribution Arbeitnow requise. Pas de couverture Maroc/Monde entier.";
    }
    @Override public Set<String> countryHints() { return ZONE; }
    @Override public boolean isReady() { return enabled; }
    @Override public String notReadyReason() { return enabled ? "" : "Arbeitnow désactivé."; }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!enabled) return List.of();
        boolean worldwide = geo.isWorldwideFilter(countryCode);
        if (!worldwide) {
            String cc = countryCode.trim().toUpperCase(Locale.ROOT);
            if (!ZONE.contains(cc)) return List.of(); // hors couverture — silence, pas de faux positifs
        }
        String cacheKey = "ARBEITNOW|" + (query == null ? "" : query.trim().toLowerCase())
                + "|" + (countryCode == null ? "ALL" : countryCode) + "|" + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) return cached.size() <= limit ? cached : cached.subList(0, limit);
        try {
            List<DiscoveredJob> out = new ArrayList<>();
            boolean wantUk = worldwide || "GB".equalsIgnoreCase(countryCode);
            boolean wantEu = worldwide || (countryCode != null && ZONE.contains(countryCode.toUpperCase(Locale.ROOT)) && !"GB".equalsIgnoreCase(countryCode));
            if (wantEu) scrape(apiEu, query, countryCode, location, Math.max(limit, 20), out, "DE");
            if (wantUk && out.size() < Math.max(limit, 20)) scrape(apiUk, query, countryCode, location, Math.max(limit, 20), out, "GB");
            cache.put(cacheKey, out, cacheTtlSec);
            return out.size() > limit ? out.subList(0, limit) : out;
        } catch (Exception e) {
            throw new IllegalStateException("Arbeitnow indisponible : " + e.getMessage(), e);
        }
    }

    private void scrape(String api, String query, String country, String city, int limit, List<DiscoveredJob> out, String defaultCc) throws Exception {
        JsonNode root = mapper.readTree(WebJobHttp.getJson(api));
        JsonNode data = root.path("data");
        if (!data.isArray()) return;
        String qFold = query == null ? "" : ma.ensat.hirelink.service.TextMatcher.fold(query);
        for (JsonNode n : data) {
            if (out.size() >= limit) return;
            String title = text(n, "title");
            String deep = WebJobHttp.deepJobUrl(text(n, "url"));
            if (title == null || deep == null) continue;
            if (!qFold.isEmpty()) {
                String blob = ma.ensat.hirelink.service.TextMatcher.fold(
                        title + " " + text(n, "company_name") + " " + joinArr(n.path("tags")) + " " + text(n, "description"));
                if (!blob.contains(qFold) && !matchesWords(qFold, blob)) continue;
            }
            String loc = text(n, "location");
            String inferred = geo.inferCountryCode(loc);
            if (inferred == null) inferred = defaultCc;
            boolean remote = n.path("remote").asBoolean(false);
            String rawDesc = text(n, "description");
            String desc = rawDesc == null ? null : (rawDesc.length() > 500 ? rawDesc.substring(0, 500) : rawDesc);
            DiscoveredJob job = new DiscoveredJob(
                    title,
                    text(n, "company_name") == null ? "Entreprise (via Arbeitnow)" : text(n, "company_name"),
                    loc,
                    loc,
                    inferred,
                    joinArr(n.path("job_types")),
                    remote ? "REMOTE" : null,
                    joinArr(n.path("tags")),
                    desc,
                    deep,
                    deep,
                    code(),
                    label(),
                    homeUrl()).withDetails(WebJobFields.date(n.get("created_at")), null, null, null);
            if (!geo.matches(job, country, city)) continue;
            out.add(job);
        }
    }

    private static boolean matchesWords(String qFold, String blob) {
        for (String w : qFold.split(" ")) {
            if (w.length() < 3) continue;
            if (!blob.contains(w)) return false;
        }
        return qFold.split(" ").length > 0;
    }

    private static String joinArr(JsonNode arr) {
        if (arr == null || !arr.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : arr) {
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
