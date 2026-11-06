package ma.ensat.hirelink.service;

import ma.ensat.hirelink.repository.ApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vélocité de réponse des entreprises, mesurée sur les dossiers réellement traités.
 *
 * L'appareil ne demande plus au recruteur de promettre un délai : il observe le délai déjà
 * constaté entre la candidature et la première réponse, et n'affiche une distinction que quand
 * l'échantillon est assez large pour être honnête. Aucune étiquette négative : une entreprise qui
 * répond peu ou lentement n'est pas signalée, elle reçoit simplement moins de valeur mise en avant.
 * Le même calcul, agrégé par recruteur plutôt que par entreprise, sert de retour au tableau de bord.
 */
@Service
public class ResponsivenessService {

    /** Fenêtre d'observation : au-delà, l'habitude de l'entreprise n'est plus d'actualité. */
    private static final Duration WINDOW = Duration.ofDays(180);
    /** En dessous de cet échantillon, le pourcentage ne veut rien dire : rien n'est affiché. */
    private static final int MIN_ANSWERED = 3;
    private static final Duration FAST = Duration.ofDays(3);
    private static final long CACHE_TTL_SECONDS = 600;

    /** {@code badge} : « REACTIF », « ATTENTIF » ou vide ; {@code medianDays} : médiane en jours. */
    public record Signal(int answered, double fastShare, double medianDays, String badge) {
        public static final Signal NONE = new Signal(0, 0, 0, "");

        public boolean labelled() {
            return !badge.isEmpty();
        }
    }

    private final ApplicationRepository applicationRepository;
    private volatile Signals cache = null;
    private volatile Instant cachedAt = Instant.EPOCH;

    /** Les deux vélocités mesurées, calculées sur la même passe de requêtes. */
    private record Signals(Map<Long, Signal> byOrg, Map<Long, Signal> byRecruiter) {
        static final Signals EMPTY = new Signals(Map.of(), Map.of());
    }

    public ResponsivenessService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public Signal signalOf(Long orgId) {
        if (orgId == null) return Signal.NONE;
        return signals().byOrg().getOrDefault(orgId, Signal.NONE);
    }

    /** La vélocité propre au recruteur connecté, sur ses propres offres — mêmes seuils que l'entreprise. */
    public Signal signalOfRecruiter(Long recruiterId) {
        if (recruiterId == null) return Signal.NONE;
        return signals().byRecruiter().getOrDefault(recruiterId, Signal.NONE);
    }

    private Signals signals() {
        Instant now = Instant.now();
        Signals current = cache;
        if (current != null && Duration.between(cachedAt, now).getSeconds() < CACHE_TTL_SECONDS) return current;
        synchronized (this) {
            if (cache != null && Duration.between(cachedAt, Instant.now()).getSeconds() < CACHE_TTL_SECONDS) return cache;
            cache = compute(Instant.now().minus(WINDOW));
            cachedAt = Instant.now();
            return cache;
        }
    }

    private Signals compute(Instant since) {
        Map<Long, List<Duration>> byOrg = new HashMap<>();
        Map<Long, List<Duration>> byRecruiter = new HashMap<>();
        for (Object[] row : applicationRepository.findDecidedDurations(since)) {
            Long orgId = row[0] == null ? null : ((Number) row[0]).longValue();
            Long recruiterId = row[1] == null ? null : ((Number) row[1]).longValue();
            Instant submittedAt = instantOf(row[2]);
            Instant decidedAt = instantOf(row[3]);
            if (submittedAt == null || decidedAt == null || decidedAt.isBefore(submittedAt)) continue;
            Duration d = Duration.between(submittedAt, decidedAt);
            if (orgId != null) byOrg.computeIfAbsent(orgId, k -> new ArrayList<>()).add(d);
            if (recruiterId != null) byRecruiter.computeIfAbsent(recruiterId, k -> new ArrayList<>()).add(d);
        }
        return new Signals(labelled(byOrg), labelled(byRecruiter));
    }

    /** Sous l'échantillon minimum, aucune étiquette : rien n'est affiché, rien n'est pénalisé. */
    private static Map<Long, Signal> labelled(Map<Long, List<Duration>> durationsByKey) {
        Map<Long, Signal> out = new HashMap<>();
        durationsByKey.forEach((key, durations) -> {
            int answered = durations.size();
            if (answered < MIN_ANSWERED) return;
            double fastShare = durations.stream().filter(d -> d.compareTo(FAST) <= 0).count() / (double) answered;
            double medianDays = median(durations);
            String badge = fastShare >= 0.70 ? "REACTIF" : fastShare >= 0.50 ? "ATTENTIF" : "";
            out.put(key, new Signal(answered, fastShare, medianDays, badge));
        });
        return out;
    }

    private static Instant instantOf(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.util.Date date) return date.toInstant();
        return null;
    }

    private static double median(List<Duration> durations) {
        List<Duration> sorted = new ArrayList<>(durations);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        double hours = n % 2 == 1
                ? sorted.get(n / 2).toMillis() / 3_600_000.0
                : (sorted.get(n / 2 - 1).toMillis() + sorted.get(n / 2).toMillis()) / 2 / 3_600_000.0;
        return Math.round(hours / 24 * 10) / 10.0;
    }
}
