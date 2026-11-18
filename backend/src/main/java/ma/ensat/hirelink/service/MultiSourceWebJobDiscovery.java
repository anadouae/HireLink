package ma.ensat.hirelink.service;

import ma.ensat.hirelink.service.web.JobGeoVerifier;
import ma.ensat.hirelink.service.web.SourceCircuitBreaker;
import ma.ensat.hirelink.service.web.WebContractLabels;
import ma.ensat.hirelink.service.web.WebJobDedupe;
import ma.ensat.hirelink.service.web.WebJobSource;
import ma.ensat.hirelink.service.web.WebSearchFallbackSource;
import ma.ensat.hirelink.service.web.WebSearchResultCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agrégateur multi-sources international.
 * N'impose jamais le Maroc. « Tous les pays » = country null / ALL.
 * Ne persiste rien. Signale honnêtement les sources utilisées et leurs limites.
 * <ul>
 *   <li>Toutes les sources prêtes sont interrogées en parallèle, chacune avec son propre délai maximum
 *       ({@code hirelink.web-search.per-source-timeout-ms}, 3 s par défaut) : une source lente ou en échec est
 *       signalée (TIMEOUT / ERROR) et les résultats des autres sont quand même renvoyés.</li>
 *   <li>Coupe-circuit par source : après N échecs consécutifs, la source est ignorée quelques minutes.</li>
 *   <li>Cache court par source et par requête normalisée (quelques minutes).</li>
 *   <li>Chaque appel est journalisé : source, durée, statut (OK / FAIL / TIMEOUT / SKIPPED), nombre d'annonces.</li>
 *   <li>Doublons entre sources (même intitulé + entreprise + lieu, ou même lien) : le premier vu est gardé.</li>
 * </ul>
 * Repli Tavily/Serper uniquement si le catalogue API est mince et la requête non vide (désactivé par défaut).
 */
@Service
@ConditionalOnProperty(name = "hirelink.web-search.enabled", havingValue = "true")
public class MultiSourceWebJobDiscovery implements WebJobDiscovery {

    private static final Logger log = LoggerFactory.getLogger(MultiSourceWebJobDiscovery.class);

    /** Délai maximum par source quand aucune configuration n'est fournie. */
    static final long DEFAULT_SOURCE_TIMEOUT_MS = 3000;
    static final int DEFAULT_BREAKER_FAILURES = 3;
    static final long DEFAULT_BREAKER_OPEN_SECONDS = 300;
    static final long DEFAULT_CACHE_TTL_SECONDS = 300;

    private final List<WebJobSource> sources;
    private final JobGeoVerifier geo;
    private final JobSearchLexicon lexicon;
    private final WebSearchFallbackSource webFallback;
    private final int perSourceCap;
    private final long perSourceTimeoutMs;
    private final SourceCircuitBreaker breaker;
    private final WebSearchResultCache cache;
    private final ExecutorService pool;

    public MultiSourceWebJobDiscovery(
            List<WebJobSource> sources,
            JobGeoVerifier geo,
            JobSearchLexicon lexicon,
            WebSearchFallbackSource webFallback,
            int perSourceCap) {
        this(sources, geo, lexicon, webFallback, perSourceCap, DEFAULT_SOURCE_TIMEOUT_MS);
    }

    public MultiSourceWebJobDiscovery(
            List<WebJobSource> sources,
            JobGeoVerifier geo,
            JobSearchLexicon lexicon,
            WebSearchFallbackSource webFallback,
            int perSourceCap,
            long perSourceTimeoutMs) {
        this(sources, geo, lexicon, webFallback, perSourceCap, perSourceTimeoutMs,
                new SourceCircuitBreaker(DEFAULT_BREAKER_FAILURES, DEFAULT_BREAKER_OPEN_SECONDS * 1000),
                new WebSearchResultCache(DEFAULT_CACHE_TTL_SECONDS * 1000, 500));
    }

    @Autowired
    public MultiSourceWebJobDiscovery(
            List<WebJobSource> sources,
            JobGeoVerifier geo,
            JobSearchLexicon lexicon,
            WebSearchFallbackSource webFallback,
            @Value("${hirelink.web-search.per-source-cap:15}") int perSourceCap,
            @Value("${hirelink.web-search.per-source-timeout-ms:3000}") long perSourceTimeoutMs,
            @Value("${hirelink.web-search.breaker.failures:3}") int breakerFailures,
            @Value("${hirelink.web-search.breaker.open-seconds:300}") long breakerOpenSeconds,
            @Value("${hirelink.web-search.cache-ttl-seconds:300}") long cacheTtlSeconds) {
        this(sources, geo, lexicon, webFallback, perSourceCap, perSourceTimeoutMs,
                new SourceCircuitBreaker(breakerFailures, Math.max(1, breakerOpenSeconds) * 1000),
                new WebSearchResultCache(Math.max(0, cacheTtlSeconds) * 1000, 500));
    }

    /** Coupe-circuit et cache fournis (tests). */
    MultiSourceWebJobDiscovery(
            List<WebJobSource> sources,
            JobGeoVerifier geo,
            JobSearchLexicon lexicon,
            WebSearchFallbackSource webFallback,
            int perSourceCap,
            long perSourceTimeoutMs,
            SourceCircuitBreaker breaker,
            WebSearchResultCache cache) {
        this.sources = sources == null ? List.of() : sources;
        this.geo = geo;
        this.lexicon = lexicon;
        this.webFallback = webFallback;
        this.perSourceCap = Math.max(3, perSourceCap);
        this.perSourceTimeoutMs = Math.max(200, perSourceTimeoutMs);
        this.breaker = breaker;
        this.cache = cache;
        // Threads démons à la demande : une source bloquée n'immobilise pas les recherches suivantes.
        AtomicInteger seq = new AtomicInteger();
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "web-source-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private record FetchOutcome(List<DiscoveredJob> batch, Exception error, boolean timedOut, long durationMs) {}

    /** Une source pour une recherche : réponse en cache, ou appel en cours. */
    private record Slot(WebJobSource src, List<DiscoveredJob> cached, CompletableFuture<FetchOutcome> future) {}

    @Override
    public DiscoveryResult search(String query, String countryCode, String location, int limit) {
        if (limit <= 0) {
            return DiscoveryResult.ok(List.of(), List.of(), coveragePreamble(countryCode, location));
        }
        String country = normalizeCountry(countryCode);
        int want = Math.max(1, limit);
        String apiQuery = lexicon.preferredApiQuery(query == null ? "" : query);
        int fetchCap = Math.min(perSourceCap, want);

        List<SourceReport> reports = new ArrayList<>();
        List<DiscoveredJob> merged = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        int ready = 0;
        int errors = 0;

        List<Slot> slots = new ArrayList<>();
        for (WebJobSource src : sources) {
            if (src == null) continue;
            String code = safeCode(src);
            boolean isReady;
            try {
                isReady = src.isReady();
            } catch (RuntimeException e) {
                isReady = false;
            }
            if (!isReady) {
                reports.add(new SourceReport(code, src.label(), "SKIPPED", src.notReadyReason(), 0));
                logCall(code, "SKIPPED", 0, 0, "désactivée");
                continue;
            }
            ready++;
            String key = WebSearchResultCache.key(code, apiQuery, country, location, fetchCap);
            List<DiscoveredJob> hit = cache.get(key);
            if (hit != null) {
                slots.add(new Slot(src, hit, null));
                continue;
            }
            if (!breaker.allow(code)) {
                errors++;
                long wait = breaker.secondsUntilRetry(code);
                reports.add(new SourceReport(code, src.label(), "SKIPPED",
                        "Source momentanément ignorée après plusieurs échecs (nouvel essai dans "
                                + Math.max(1, (wait + 59) / 60) + " min).", 0));
                logCall(code, "SKIPPED", 0, 0, "coupe-circuit ouvert");
                continue;
            }
            CompletableFuture<FetchOutcome> future = CompletableFuture.supplyAsync(() -> {
                long t0 = System.nanoTime();
                try {
                    List<DiscoveredJob> batch = src.fetch(apiQuery, country, location, fetchCap);
                    // Si la variante API ne donne rien et que la requête d'origine diffère, 2e essai.
                    if ((batch == null || batch.isEmpty())
                            && query != null && !query.isBlank()
                            && !apiQuery.equalsIgnoreCase(query.trim())) {
                        batch = src.fetch(query.trim(), country, location, fetchCap);
                    }
                    List<DiscoveredJob> safe = batch == null ? List.of() : batch;
                    // Même arrivée après le délai, la réponse sert à la recherche suivante.
                    cache.put(key, safe);
                    return new FetchOutcome(safe, null, false, elapsedMs(t0));
                } catch (Exception e) {
                    return new FetchOutcome(List.of(), e, false, elapsedMs(t0));
                } catch (Throwable t) {
                    return new FetchOutcome(List.of(), new RuntimeException(t), false, elapsedMs(t0));
                }
            }, pool)
                    // Délai propre à chaque source : au-delà, la source est notée TIMEOUT et on continue sans elle.
                    .completeOnTimeout(new FetchOutcome(List.of(), null, true, perSourceTimeoutMs),
                            perSourceTimeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(e -> new FetchOutcome(List.of(),
                            e instanceof Exception ex ? ex : new RuntimeException(e), false, 0));
            slots.add(new Slot(src, null, future));
        }

        List<CompletableFuture<?>> running = new ArrayList<>();
        for (Slot s : slots) if (s.future() != null) running.add(s.future());
        if (!running.isEmpty()) {
            try {
                CompletableFuture.allOf(running.toArray(CompletableFuture[]::new))
                        .get(perSourceTimeoutMs + 500, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // On consomme ce qui est déjà terminé ; les appels non finis sont notés TIMEOUT ci-dessous.
            }
        }

        // Fusion dans l'ordre des sources : le premier exemplaire d'une annonce en double est gardé.
        for (Slot slot : slots) {
            WebJobSource src = slot.src();
            String code = safeCode(src);
            List<DiscoveredJob> batch;
            if (slot.cached() != null) {
                batch = slot.cached();
                logCall(code, "OK", 0, batch.size(), "cache");
            } else {
                FetchOutcome outcome = null;
                CompletableFuture<FetchOutcome> f = slot.future();
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    outcome = f.getNow(null);
                } else {
                    f.cancel(true);
                }
                if (outcome == null || outcome.timedOut()) {
                    errors++;
                    breaker.recordFailure(code);
                    logCall(code, "TIMEOUT", perSourceTimeoutMs, 0, null);
                    reports.add(new SourceReport(code, src.label(), "TIMEOUT",
                            "Source trop lente (plus de " + (perSourceTimeoutMs / 1000.0) + " s) : ignorée pour cette recherche.", 0));
                    continue;
                }
                if (outcome.error() != null) {
                    errors++;
                    breaker.recordFailure(code);
                    String msg = outcome.error().getMessage();
                    logCall(code, "FAIL", outcome.durationMs(), 0,
                            msg == null ? outcome.error().getClass().getSimpleName() : msg);
                    reports.add(new SourceReport(code, src.label(), "ERROR",
                            msg == null ? outcome.error().getClass().getSimpleName() : msg, 0));
                    continue;
                }
                breaker.recordSuccess(code);
                batch = outcome.batch();
                logCall(code, "OK", outcome.durationMs(), batch.size(), null);
            }
            int added = 0;
            for (DiscoveredJob raw : batch) {
                if (raw == null) continue;
                // Une annonce mal formée ne doit pas faire échouer toute la recherche.
                try {
                    DiscoveredJob job = raw.withContractType(WebContractLabels.french(raw.contractType()));
                    if (!geo.matches(job, country, location)) continue;
                    String dedupeKey = WebJobDedupe.key(job.title(), job.companyName(), job.location());
                    if (seenKeys.contains(dedupeKey) || isDuplicate(merged, job)) continue;
                    seenKeys.add(dedupeKey);
                    merged.add(job);
                    added++;
                } catch (RuntimeException badJob) {
                    continue;
                }
                if (merged.size() >= want * 3) break;
            }
            reports.add(new SourceReport(code, src.label(), "OK", src.coverageNote(), added));
        }

        // Repli Web (Tavily/Serper) : seulement si requête non vide et catalogue API mince.
        // Jamais sur q vide (évite le déclenchement à chaque frappe / listage brut).
        if (query != null && !query.isBlank()
                && webFallback != null
                && webFallback.isReady()
                && merged.size() < webFallback.thinThreshold()) {
            try {
                List<DiscoveredJob> extra = CompletableFuture
                        .supplyAsync(() -> webFallback.search(query, country, location,
                                Math.min(want, webFallback.thinThreshold())), pool)
                        .get(perSourceTimeoutMs, TimeUnit.MILLISECONDS);
                int added = 0;
                for (DiscoveredJob job : extra == null ? List.<DiscoveredJob>of() : extra) {
                    if (job == null) continue;
                    try {
                        if (!geo.matches(job, country, location)) continue;
                        if (isDuplicate(merged, job)) continue;
                    } catch (RuntimeException badJob) {
                        continue;
                    }
                    merged.add(job);
                    added++;
                }
                reports.add(new SourceReport(webFallback.code(), webFallback.providerLabel(), "OK",
                        "Repli Web (catalogue API mince < " + webFallback.thinThreshold() + ").", added));
            } catch (java.util.concurrent.TimeoutException e) {
                reports.add(new SourceReport(webFallback.code(), webFallback.providerLabel(), "TIMEOUT",
                        "Repli Web trop lent : ignoré pour cette recherche.", 0));
            } catch (Exception e) {
                Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
                String msg = cause.getMessage();
                reports.add(new SourceReport(webFallback.code(), webFallback.providerLabel(), "ERROR",
                        msg == null ? cause.getClass().getSimpleName() : msg, 0));
            }
        } else if (webFallback != null && !webFallback.isReady()) {
            reports.add(new SourceReport(webFallback.code(), "Web (Tavily/Serper)", "SKIPPED",
                    webFallback.notReadyReason(), 0));
        }

        String summary = humanCoverageSummary(country, location, ready, errors, merged.size(), reports);

        if (ready == 0 && (webFallback == null || !webFallback.isReady())) {
            return DiscoveryResult.unavailable(
                    "La recherche Web directe est indisponible pour le moment. " + summary);
        }
        if (merged.isEmpty() && errors >= Math.max(1, ready) && (webFallback == null || !webFallback.isReady())) {
            String msg = "La recherche Web n’a renvoyé aucune annonce exploitable. " + summary;
            // État de chaque source conservé (TIMEOUT / ERROR / SKIPPED) pour le diagnostic.
            return new DiscoveryResult(false, "ERROR", msg, List.of(), reports, msg);
        }
        return DiscoveryResult.ok(merged, reports, summary.trim());
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static String safeCode(WebJobSource src) {
        try {
            String c = src.code();
            return c == null || c.isBlank() ? src.getClass().getSimpleName() : c;
        } catch (RuntimeException e) {
            return src.getClass().getSimpleName();
        }
    }

    /** Une ligne INFO par source et par recherche : source, statut, durée, nombre d'annonces. */
    private static void logCall(String source, String status, long durationMs, int count, String detail) {
        if (detail == null || detail.isBlank()) {
            log.info("Recherche web : source={} statut={} durée={} ms annonces={}", source, status, durationMs, count);
        } else {
            String d = detail.length() > 160 ? detail.substring(0, 160) + "…" : detail;
            log.info("Recherche web : source={} statut={} durée={} ms annonces={} ({})", source, status, durationMs, count, d);
        }
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
            sb.append(hits).append(" offre").append(hits > 1 ? "s" : "")
                    .append(" web trouvée").append(hits > 1 ? "s" : "")
                    .append(" en direct via ").append(names).append('.');
        } else if (ready > 0) {
            sb.append("Aucune offre web trouvée en direct pour cette recherche.");
        } else {
            sb.append("Aucun fournisseur Web n’est prêt.");
        }
        if (errors > 0) {
            sb.append(" Certains fournisseurs n’ont pas répondu.");
        }
        sb.append(" Recherche en direct sur quelques sites d’emploi publics : rien n’est stocké.");
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
