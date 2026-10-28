package ma.ensat.hirelink.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limiteur de débit / verrouillage anti-force-brute en mémoire (fenêtre glissante).
 * Les clés sont indépendantes de l'existence du compte : aucune différence observable pour un attaquant
 * (pas d'énumération). Le compteur repart à zéro au redémarrage du serveur.
 */
@Component
public class AuthThrottle {

    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

    /** Lève 429 si {@code max} évènements ou plus ont eu lieu pendant la fenêtre. */
    public void assertAllowed(String key, int max, long windowSeconds, String message) {
        long now = System.currentTimeMillis();
        Deque<Long> q = events.get(key);
        if (q == null) return;
        synchronized (q) {
            prune(q, now, windowSeconds);
            if (q.size() >= max) {
                long oldest = q.peekFirst();
                long retry = (oldest + windowSeconds * 1000 - now + 999) / 1000;
                throw new TooManyRequestsException(message, retry);
            }
        }
    }

    public void record(String key, long windowSeconds) {
        long now = System.currentTimeMillis();
        if (events.size() > 50_000) events.entrySet().removeIf(e -> e.getValue().isEmpty());
        Deque<Long> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now, windowSeconds);
            q.addLast(now);
        }
    }

    /** Compte + vérifie en une seule opération (limite « n requêtes par fenêtre »). */
    public void hit(String key, int max, long windowSeconds, String message) {
        assertAllowed(key, max, windowSeconds, message);
        record(key, windowSeconds);
    }

    public void reset(String key) {
        events.remove(key);
    }

    private static void prune(Deque<Long> q, long now, long windowSeconds) {
        long limit = now - windowSeconds * 1000;
        Iterator<Long> it = q.iterator();
        while (it.hasNext() && it.next() < limit) it.remove();
    }
}
