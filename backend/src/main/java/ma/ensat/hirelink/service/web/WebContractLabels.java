package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.TextMatcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Libellés de contrat des sources Web (« full_time », « Full-Time », « permanent », « Vollzeit »…)
 * ramenés aux libellés français de l'écran de recherche. Valeur inconnue = null (« Non précisé »),
 * pour ne jamais afficher un code technique ni une catégorie métier à la place d'un contrat.
 */
public final class WebContractLabels {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        put("CDI", "cdi", "permanent", "festanstellung", "unbefristet");
        put("CDD", "cdd", "temporary", "temp", "fixed term", "befristet", "interim");
        put("Stage", "stage", "internship", "intern", "praktikum", "trainee", "pfe");
        put("Alternance", "alternance", "apprenticeship", "apprentice", "ausbildung", "work study");
        put("Freelance", "freelance", "freelancer", "contract", "contractor", "contracts", "self employed", "freiberuflich");
        put("Temps partiel", "temps partiel", "part time", "parttime", "teilzeit", "mini job", "minijob");
        put("Temps plein", "temps plein", "full time", "fulltime", "vollzeit");
    }

    private static void put(String label, String... keys) {
        for (String k : keys) LABELS.putIfAbsent(k, label);
    }

    private WebContractLabels() {}

    /**
     * Libellé français, ou null si la valeur ne désigne pas un type de contrat connu.
     * Une liste (« full time, berufserfahren ») donne le premier type reconnu.
     */
    public static String french(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (String part : raw.split("[,;/|]")) {
            String k = TextMatcher.fold(part.replace('_', ' ').replace('-', ' ')).replaceAll("\\s+", " ").trim();
            if (k.isEmpty()) continue;
            String hit = LABELS.get(k);
            if (hit != null) return hit;
        }
        return null;
    }
}
