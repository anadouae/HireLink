package ma.ensat.hirelink.service;

import ma.ensat.hirelink.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Suggestions de noms d'entreprises uniquement (jamais des titres d'offres).
 * Sources : entreprises HireLink actives et entreprises des résultats de la recherche en cours.
 * Classement : préfixe du nom, puis début de mot, puis sous-chaîne ; dédoublonnage insensible à la casse et aux accents.
 */
@Service
public class CompanySuggestService {

    public static final int MAX_SUGGESTIONS = 8;
    public static final int MIN_CHARS = 2;
    /** Plafond des noms reçus du navigateur (résultats affichés). */
    private static final int CONTEXT_MAX = 100;

    private final OrganizationRepository organizations;

    public CompanySuggestService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /**
     * @param query   texte saisi dans le champ Entreprise (2 caractères minimum)
     * @param context noms d'entreprises des résultats affichés, séparés par « | » (offres Web comprises)
     */
    public List<String> suggest(String query, String context) {
        String q = query == null ? "" : query.trim();
        if (TextMatcher.fold(q).length() < MIN_CHARS) return List.of();

        List<String> activeNames = organizations.findActiveNames();
        Set<String> active = activeNames.stream()
                .map(CompanySuggestService::foldKey)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        // Une entreprise désactivée n'est jamais proposée, quelle que soit la source du nom.
        Set<String> inactive = organizations.findInactiveNames().stream()
                .map(CompanySuggestService::foldKey)
                .filter(s -> !s.isEmpty() && !active.contains(s))
                .collect(Collectors.toSet());

        Map<String, String> unique = new LinkedHashMap<>();
        addNames(unique, activeNames, inactive);
        addNames(unique, splitContext(context), inactive);
        return rankNames(q, unique.values(), MAX_SUGGESTIONS);
    }

    static List<String> rankNames(String query, Collection<String> names, int limit) {
        String q = TextMatcher.fold(query);
        if (q.length() < MIN_CHARS) return List.of();
        record Hit(String label, int rank, String fold) {}
        Map<String, Hit> best = new LinkedHashMap<>();
        for (String raw : names) {
            String label = clean(raw);
            if (label.isEmpty()) continue;
            int rank = rank(q, label);
            if (rank <= 0) continue;
            String key = foldKey(label);
            Hit prev = best.get(key);
            if (prev == null || rank > prev.rank) {
                best.put(key, new Hit(label, rank, key));
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingInt(Hit::rank).reversed()
                        .thenComparing(Hit::label, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .map(Hit::label)
                .toList();
    }

    /** 3 = préfixe du nom, 2 = début de mot, 1 = sous-chaîne, 0 = aucun. */
    static int rank(String foldedQuery, String name) {
        String n = TextMatcher.fold(name);
        if (foldedQuery.isEmpty() || n.isEmpty()) return 0;
        if (n.startsWith(foldedQuery)) return 3;
        for (String word : n.split(" ")) {
            if (word.startsWith(foldedQuery)) return 2;
        }
        return n.contains(foldedQuery) ? 1 : 0;
    }

    private static void addNames(Map<String, String> unique, Collection<String> names, Set<String> inactive) {
        if (names == null) return;
        for (String raw : names) {
            String label = clean(raw);
            if (label.isEmpty()) continue;
            String key = foldKey(label);
            if (key.isEmpty() || inactive.contains(key)) continue;
            unique.putIfAbsent(key, label);
        }
    }

    private static List<String> splitContext(String context) {
        if (context == null || context.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : context.split("[|]")) {
            String t = clean(part);
            if (t.length() > 200) continue;
            if (!t.isEmpty()) out.add(t);
            if (out.size() >= CONTEXT_MAX) break;
        }
        return out;
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String t = raw.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return "";
        String fold = TextMatcher.fold(t);
        if (fold.equals("entreprise non precisee") || fold.equals("n a") || fold.equals("na")
                || fold.equals("unknown") || fold.equals("n/a")) {
            return "";
        }
        return t;
    }

    private static String foldKey(String name) {
        return TextMatcher.fold(name == null ? "" : name).toLowerCase(Locale.ROOT);
    }
}
