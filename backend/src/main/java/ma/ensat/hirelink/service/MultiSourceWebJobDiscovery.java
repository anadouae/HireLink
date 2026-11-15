package ma.ensat.hirelink.service;

import ma.ensat.hirelink.service.web.JobGeoVerifier;
import ma.ensat.hirelink.service.web.WebJobSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrégateur multi-sources international.
 * N'impose jamais le Maroc. « Tous les pays » = country null / ALL.
 * Ne persiste rien. Signale honnêtement les sources utilisées et leurs limites.
 */
@Service
@ConditionalOnProperty(name = "hirelink.web-search.enabled", havingValue = "true")
public class MultiSourceWebJobDiscovery implements WebJobDiscovery {

    private final List<WebJobSource> sources;
    private final JobGeoVerifier geo;
    private final JobSearchLexicon lexicon;
    private final int perSourceCap;

    public MultiSourceWebJobDiscovery(
            List<WebJobSource> sources,
            JobGeoVerifier geo,
            JobSearchLexicon lexicon,
            @Value("${hirelink.web-search.per-source-cap:15}") int perSourceCap) {
        this.sources = sources;
        this.geo = geo;
        this.lexicon = lexicon;
        this.perSourceCap = Math.max(3, perSourceCap);
    }

    @Override
    public DiscoveryResult search(String query, String countryCode, String location, int limit) {
        if (limit <= 0) {
            return DiscoveryResult.ok(List.of(), List.of(), coveragePreamble(countryCode, location));
        }
        String country = normalizeCountry(countryCode);
        int want = Math.max(1, limit);
        List<SourceReport> reports = new ArrayList<>();
        List<DiscoveredJob> merged = new ArrayList<>();
        int ready = 0;
        int errors = 0;

        for (WebJobSource src : sources) {
            if (!src.isReady()) {
                reports.add(new SourceReport(src.code(), src.label(), "SKIPPED", src.notReadyReason(), 0));
                continue;
            }
            ready++;
            try {
                int fetchCap = Math.min(perSourceCap, want);
                List<DiscoveredJob> batch = src.fetch(query, country, location, fetchCap);
                int added = 0;
                for (DiscoveredJob job : batch) {
                    if (job == null) continue;
                    if (!geo.matches(job, country, location)) continue;
                    if (isDuplicate(merged, job)) continue;
                    merged.add(job);
                    added++;
                    if (merged.size() >= want * 3) break; // marge pour ranking ultérieur
                }
                reports.add(new SourceReport(src.code(), src.label(), "OK", src.coverageNote(), added));
            } catch (Exception e) {
                errors++;
                reports.add(new SourceReport(src.code(), src.label(), "ERROR",
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 0));
            }
        }

        String summary = coveragePreamble(country, location) + " "
                + "Sources prêtes : " + ready + "/" + sources.size()
                + (errors > 0 ? " (" + errors + " en erreur)." : ".")
                + " Couverture partielle du Web uniquement — pas un index mondial exhaustif.";

        if (ready == 0) {
            return DiscoveryResult.unavailable(
                    "Aucun fournisseur Web prêt. " + summary + " " + reportsSummary(reports));
        }
        if (merged.isEmpty() && errors == ready) {
            return DiscoveryResult.error("Tous les fournisseurs Web ont échoué. " + summary);
        }
        return DiscoveryResult.ok(merged, reports, summary.trim());
    }

    private String normalizeCountry(String countryCode) {
        if (geo.isWorldwideFilter(countryCode)) return null;
        return countryCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String coveragePreamble(String country, String location) {
        if (geo.isWorldwideFilter(country)) {
            return "Filtre géographique : tous les pays"
                    + (location == null || location.isBlank() ? "" : " · ville « " + location + " »") + ".";
        }
        return "Filtre géographique : " + country
                + (location == null || location.isBlank() ? "" : " · " + location)
                + " (offres non vérifiées géographiquement exclues).";
    }

    private boolean isDuplicate(List<DiscoveredJob> have, DiscoveredJob cand) {
        String url = cand.applyUrl() != null ? cand.applyUrl() : cand.originUrl();
        String urlN = url == null ? "" : url.trim().toLowerCase(java.util.Locale.ROOT);
        for (DiscoveredJob j : have) {
            String u = j.applyUrl() != null ? j.applyUrl() : j.originUrl();
            if (!urlN.isEmpty() && u != null && urlN.equals(u.trim().toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            if (lexicon.nearDuplicate(j.title(), j.companyName(), cand.title(), cand.companyName())) {
                return true;
            }
        }
        return false;
    }

    private static String reportsSummary(List<SourceReport> reports) {
        StringBuilder sb = new StringBuilder();
        for (SourceReport r : reports) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(r.code()).append('=').append(r.status()).append('.');
        }
        return sb.toString();
    }
}
