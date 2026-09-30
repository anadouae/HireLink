package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Appariement local de compétences (indice de Jaccard) — F14.
 * Aucune API tierce — ne consomme aucun crédit IA (R08).
 */
@Service
public class SkillMatchingService {

    public record MatchDetail(double score, List<String> covered, List<String> missing) {}

    /**
     * @return score entre 0.0 et 100.0
     */
    public double computeMatchScore(String candidateSkillsCsv, String requiredSkillsCsv) {
        return explain(candidateSkillsCsv, requiredSkillsCsv).score();
    }

    /** Détail vérifiable pour matching_analyses (F14). */
    public MatchDetail explain(String candidateSkillsCsv, String requiredSkillsCsv) {
        Set<String> candidate = tokenize(candidateSkillsCsv);
        Set<String> required = tokenize(requiredSkillsCsv);
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String r : required) {
            if (candidate.contains(r)) covered.add(r);
            else missing.add(r);
        }
        if (required.isEmpty()) {
            return new MatchDetail(0.0, covered, missing);
        }
        Set<String> union = new java.util.HashSet<>(candidate);
        union.addAll(required);
        double jaccard = union.isEmpty() ? 0.0 : (double) covered.size() / (double) union.size();
        double score = Math.round(jaccard * 10000.0) / 100.0;
        return new MatchDetail(score, covered, missing);
    }

    private Set<String> tokenize(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split("[,;|/]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
