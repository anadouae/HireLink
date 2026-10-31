package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Appariement local profil ↔ offre (F14) : part des compétences demandées que le candidat possède.
 * Les écritures différentes d'une même compétence sont rapprochées par le référentiel (JS = JavaScript,
 * Spring = Spring Boot, fautes de frappe légères) ; le niveau d'une langue n'intervient pas.
 * Aucune API tierce — ne consomme aucun crédit IA (R08).
 */
@Service
public class SkillMatchingService {

    public record MatchDetail(double score, List<String> covered, List<String> missing) {}

    private final SkillCatalogService catalog;

    public SkillMatchingService(SkillCatalogService catalog) {
        this.catalog = catalog;
    }

    /** @return score entre 0.0 et 100.0 */
    public double computeMatchScore(String candidateSkillsCsv, String requiredSkillsCsv) {
        return explain(candidateSkillsCsv, requiredSkillsCsv).score();
    }

    /** Détail vérifiable pour matching_analyses (F14) : compétences couvertes et manquantes, noms normalisés. */
    public MatchDetail explain(String candidateSkillsCsv, String requiredSkillsCsv) {
        Set<String> candidate = new HashSet<>();
        for (String raw : split(candidateSkillsCsv)) candidate.add(catalog.key(raw));
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : split(requiredSkillsCsv)) {
            if (!seen.add(catalog.key(raw))) continue;
            String name = catalog.normalise(raw);
            (candidate.contains(catalog.key(raw)) ? covered : missing).add(name);
        }
        int total = covered.size() + missing.size();
        double score = total == 0 ? 0.0 : Math.round(10000.0 * covered.size() / total) / 100.0;
        return new MatchDetail(score, covered, missing);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
