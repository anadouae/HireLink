package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache mémoire court pour respecter les quotas des APIs publiques
 * (Remotive conseille ≤4 req/jour et bloque &gt;2/min).
 */
@Component
public class WebJobFetchCache {

    private record Entry(Instant expiresAt, List<DiscoveredJob> jobs) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public List<DiscoveredJob> get(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (Instant.now().isAfter(e.expiresAt())) {
            store.remove(key);
            return null;
        }
        return e.jobs();
    }

    public void put(String key, List<DiscoveredJob> jobs, long ttlSeconds) {
        store.put(key, new Entry(Instant.now().plusSeconds(Math.max(60, ttlSeconds)),
                jobs == null ? List.of() : List.copyOf(jobs)));
    }
}
