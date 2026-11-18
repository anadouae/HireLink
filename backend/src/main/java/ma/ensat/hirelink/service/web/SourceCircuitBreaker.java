package ma.ensat.hirelink.service.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Coupe-circuit par source Web : après {@code failureThreshold} échecs consécutifs (erreur ou délai dépassé),
 * la source est ignorée pendant {@code openMillis}. Ensuite un seul essai est autorisé : succès = réouverture
 * normale, échec = nouvelle pause. Une source en panne ne ralentit donc plus chaque recherche.
 */
public class SourceCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final class Entry {
        int consecutiveFailures;
        long openUntil;
        boolean trialInFlight;
    }

    private final int failureThreshold;
    private final long openMillis;
    private final LongSupplier clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public SourceCircuitBreaker(int failureThreshold, long openMillis) {
        this(failureThreshold, openMillis, System::currentTimeMillis);
    }

    public SourceCircuitBreaker(int failureThreshold, long openMillis, LongSupplier clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMillis = Math.max(1000, openMillis);
        this.clock = clock;
    }

    /** true si la source peut être interrogée maintenant. */
    public boolean allow(String source) {
        Entry e = entries.computeIfAbsent(source, k -> new Entry());
        synchronized (e) {
            if (e.openUntil == 0) return true;
            if (clock.getAsLong() < e.openUntil) return false;
            // Pause écoulée : un seul essai à la fois.
            if (e.trialInFlight) return false;
            e.trialInFlight = true;
            return true;
        }
    }

    public void recordSuccess(String source) {
        Entry e = entries.computeIfAbsent(source, k -> new Entry());
        synchronized (e) {
            e.consecutiveFailures = 0;
            e.openUntil = 0;
            e.trialInFlight = false;
        }
    }

    public void recordFailure(String source) {
        Entry e = entries.computeIfAbsent(source, k -> new Entry());
        synchronized (e) {
            e.consecutiveFailures++;
            boolean trial = e.trialInFlight;
            e.trialInFlight = false;
            if (trial || e.consecutiveFailures >= failureThreshold) {
                e.openUntil = clock.getAsLong() + openMillis;
            }
        }
    }

    public State state(String source) {
        Entry e = entries.get(source);
        if (e == null) return State.CLOSED;
        synchronized (e) {
            if (e.openUntil == 0) return State.CLOSED;
            return clock.getAsLong() < e.openUntil ? State.OPEN : State.HALF_OPEN;
        }
    }

    /** Secondes restantes avant le prochain essai (0 si la source est disponible). */
    public long secondsUntilRetry(String source) {
        Entry e = entries.get(source);
        if (e == null) return 0;
        synchronized (e) {
            long left = e.openUntil - clock.getAsLong();
            return left <= 0 ? 0 : (left + 999) / 1000;
        }
    }

    public int failureThreshold() { return failureThreshold; }

    public long openMillis() { return openMillis; }
}
