package ma.ensat.hirelink.service;

import ma.ensat.hirelink.service.web.JobGeoVerifier;
import ma.ensat.hirelink.service.web.WebJobSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agrégateur multi-sources international.
 * N'impose jamais le Maroc. « Tous les pays » = country null / ALL.
 * Ne persiste rien. Signale honnêtement les sources utilisées et leurs limites.
 * Les appels sources sont parallélisés pour rester sous les délais UI.
 */
@Service
@ConditionalOnProperty(name = "hirelink.web-search.enabled", havingValue = "true")
public class MultiSourceWebJobDiscovery implements WebJobDiscovery {

    private final List<WebJobSource> sources;
    private final JobGeoVerifier geo;
    private final JobSearchLexicon lexicon;
    private final int perSourceCap;
    private final ExecutorService pool;

    public MultiSourceWebJobDiscovery(
            List<WebJobSource> sources,
            JobGeoVerifier geo,
            JobSearchLexicon lexicon,
            @Value("${hirelink.web-search.per-source-cap:15}") int perSourceCap) {
        this.sources = sources;
        this.geo = geo;
        this.lexicon = lexicon;
        this.perSourceCap = Math.max(3, perSourceCap);
        this.pool = Executors.newFixedThreadPool(Math.max(2, Math.min(6, Math.max(2, sources.size()))));
    }

    @Override
    public DiscoveryResult search(String query, String countryCode, String location, int limit) {
        if (limit <= 0) {
            return DiscoveryResult.ok(List.of(), List.of(), coveragePreamble(countryCode, location));
        }
        String country = normalizeCountry(countryCode);
        int want = Math.max(1, limit);
        String apiQuery = lexicon.preferredApiQuery(query == null ? "" : query);

        List<SourceReport> reports = new ArrayList<>();
        List<DiscoveredJob> merged = new ArrayList<>();
        AtomicInteger ready = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        record FetchOutcome(WebJobSource src, List<DiscoveredJob> batch, Exception error, boolean skipped, String skipReason) {}

        List<CompletableFuture<FetchOutcome>> futures = new ArrayList<>();
        for (WebJobSource src : sources) {
            if (!src.isReady()) {
                reports.add(new SourceReport(src.code(), src.label(), "SKIPPED", src.notReadyReason(), 0));
                continue;
            }
            ready.incrementAndGet();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    int fetchCap = Math.min(perSourceCap, want);
                    List<DiscoveredJob> batch = src.fetch(apiQuery, country, location, fetchCap);
                    // Si la variante API ne donne rien et que la requête d'origine diffère, 2e essai.
                    if ((batch == null || batch.isEmpty())
                            && query != null && !query.isBlank()
                            && !apiQuery.equalsIgnoreCase(query.trim())) {
                        batch = src.fetch(query.trim(), country, location, fetchCap);
                    }
                    return new FetchOutcome(src, batch == null ? List.of() : batch, null, false, "");
                } catch (Exception e) {
                    return new FetchOutcome(src, List.of(), e, false, "");
                }
            }, pool));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(28, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // On consomme ce qui est déjà terminé ; les futures non finies seront traitées ci-dessous.
        }

        for (CompletableFuture<FetchOutcome> f : futures) {
            FetchOutcome outcome;
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try {
                    outcome = f.getNow(null);
                } catch (Exception e) {
                    errors.incrementAndGet();
                    continue;
                }
            } else {
                errors.incrementAndGet();
                // Annulation douce : on ne bloque pas plus longtemps
                f.cancel(true);
                continue;
            }
            if (outcome == null) {
                errors.incrementAndGet();
                continue;
            }
            WebJobSource src = outcome.src();
            if (outcome.error() != null) {
                errors.incrementAndGet();
                String msg = outcome.error().getMessage();
                reports.add(new SourceReport(src.code(), src.label(), "ERROR",
                        msg == null ? outcome.error().getClass().getSimpleName() : msg, 0));
                continue;
            }
            int added = 0;
            for (DiscoveredJob job : outcome.batch()) {
                if (job == null) continue;
                if (!geo.matches(job, country, location)) continue;
                if (isDuplicate(merged, job)) continue;
                merged.add(job);
                added++;
                if (merged.size() >= want * 3) break;
            }
            reports.add(new SourceReport(src.code(), src.label(), "OK", src.coverageNote(), added));
        }

        String summary = humanCoverageSummary(country, location, ready.get(), errors.get(), merged.size(), reports);

        if (ready.get() == 0) {
            return DiscoveryResult.unavailable(
                    "La recherche Web directe est indisponible pour le moment. " + summary);
        }
        if (merged.isEmpty() && errors.get() >= ready.get()) {
            return DiscoveryResult.error(
                    "La recherche Web n’a renvoyé aucune annonce exploitable. " + summary);
        }
        return DiscoveryResult.ok(merged, reports, summary.trim());
    }

    /** Résumé destinée au candidat : libellés métier, sans codes techniques OK/SKIPPED. */
    private String humanCoverageSummary(
            String country, String location, int ready, int errors, int hits, List<SourceReport> reports) {
        StringBuilder names = new StringBuilder();
        int contributing = 0;
        for (SourceReport r : reports) {
            if (r == null || !"OK".equals(r.status()) || r.hits() <= 0) continue;
            if (contributing > 0) names.append(", ");
            names.append(r.label() != null && !r.label().isBlank() ? r.label() : r.code());
            contributing++;
        }
        StringBuilder sb = new StringBuilder(coveragePreamble(country, location));
        sb.append(' ');
        if (hits > 0 && contributing > 0) {
            sb.append(hits).append(" annonce").append(hits > 1 ? "s" : "")
                    .append(" Web temporaire").append(hits > 1 ? "s" : "")
                    .append(" via ").append(names).append('.');
        } else if (ready > 0) {
            sb.append("Aucune annonce Web temporaire pour cette recherche.");
        } else {
            sb.append("Aucun fournisseur Web n’est prêt.");
        }
        if (errors > 0) {
            sb.append(" Certains fournisseurs n’ont pas répondu.");
        }
        sb.append(" Couverture partielle — HireLink n’indexe pas tout le Web.");
        return sb.toString().trim();
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

}
