package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.TextMatcher;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Cache court (quelques minutes) des réponses de chaque source, par requête normalisée
 * (mots-clés repliés + pays + lieu) : relancer la même recherche, changer de page ou ajuster un filtre
 * appliqué après coup ne rappelle pas les sites. Rien n'est écrit en base.
 */
public class WebSearchResultCache {

    private record Entry(long expiresAt, List<DiscoveredJob> jobs) {}

    private final long ttlMillis;
    private final int maxEntries;
    private final LongSupplier clock;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public WebSearchResultCache(long ttlMillis, int maxEntries) {
        this(ttlMillis, maxEntries, System::currentTimeMillis);
    }

    public WebSearchResultCache(long ttlMillis, int maxEntries, LongSupplier clock) {
        this.ttlMillis = Math.max(0, ttlMillis);
        this.maxEntries = Math.max(10, maxEntries);
        this.clock = clock;
    }

    /** Clé : source + requête repliée (accents, casse, espaces) + pays + lieu + plafond demandé. */
    public static String key(String source, String query, String country, String location, int cap) {
        return (source == null ? "" : source) + "|" + norm(query) + "|"
                + (country == null ? "ALL" : country.trim().toUpperCase(java.util.Locale.ROOT)) + "|"
                + norm(location) + "|" + cap;
    }

    private static String norm(String s) {
        return s == null ? "" : TextMatcher.fold(s).replaceAll("\\s+", " ").trim();
    }

    public List<DiscoveredJob> get(String key) {
        if (ttlMillis == 0) return null;
        Entry e = store.get(key);
        if (e == null) return null;
        if (clock.getAsLong() >= e.expiresAt()) {
            store.remove(key, e);
            return null;
        }
        return e.jobs();
    }

    public void put(String key, List<DiscoveredJob> jobs) {
        if (ttlMillis == 0) return;
        long now = clock.getAsLong();
        if (store.size() >= maxEntries) {
            store.entrySet().removeIf(en -> now >= en.getValue().expiresAt());
            if (store.size() >= maxEntries) {
                // Toujours borné : on retire l'entrée qui expire le plus tôt.
                store.entrySet().stream()
                        .min(java.util.Comparator.comparingLong(en -> en.getValue().expiresAt()))
                        .ifPresent(en -> store.remove(en.getKey()));
            }
        }
        store.put(key, new Entry(now + ttlMillis, jobs == null ? List.of() : List.copyOf(jobs)));
    }

    public int size() { return store.size(); }
}
