package ma.ensat.hirelink.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lexique léger FR/EN pour élargir une requête catalogue (synonymes métier / compétences)
 * sans embeddings ni infra IA. Les variantes sont repliées ({@link TextMatcher#fold}).
 */
@Component
public class JobSearchLexicon {

    /** Mots grammaticaux ignorés (évite que « de » score toutes les offres FR). */
    private static final Set<String> STOP = Set.of(
            "de", "des", "du", "la", "le", "les", "un", "une", "et", "ou", "a", "au", "aux",
            "en", "pour", "par", "sur", "avec", "dans", "the", "and", "or", "of", "to", "for");

    /** Groupes de synonymes : toute forme du groupe élargit la requête aux autres. */
    private static final List<Set<String>> GROUPS = List.of(
            set("developpeur", "developer", "dev", "programmeur", "software engineer", "ingenieur logiciel",
                    "fullstack", "full stack", "full-stack", "backend", "frontend", "front end"),
            set("data", "donnees", "data analyst", "analyste donnees", "data scientist", "scientifique donnees",
                    "business intelligence", "bi", "etl"),
            set("product", "produit", "product owner", "product manager", "chef de produit", "po", "pm"),
            set("designer", "design", "ux", "ui", "ux designer", "ui designer", "product designer", "graphiste"),
            set("rh", "ressources humaines", "recruteur", "recruteuse", "talent acquisition", "chargé de recrutement"),
            set("marketing", "digital marketing", "growth", "community manager", "seo", "sea"),
            set("commercial", "vente", "sales", "account manager", "business developer", "bizdev"),
            set("devops", "sre", "cloud", "infrastructure", "sysadmin", "administrateur systeme"),
            set("cybersecurite", "cyber", "securite", "security", "pentester", "soc"),
            set("java", "spring", "jee", "jakarta"),
            set("javascript", "js", "typescript", "ts", "node", "nodejs", "react", "angular", "vue"),
            set("python", "django", "flask", "fastapi"),
            set("stage", "internship", "intern", "alternance", "apprentissage"),
            set("cdi", "contrat a duree indeterminee", "permanent"),
            set("cdd", "contrat a duree determinee", "temporary"),
            set("remote", "teletravail", "wfh", "a distance"),
            set("hybride", "hybrid"),
            set("casablanca", "casa"),
            set("rabat", "sale"),
            set("tetouan", "tetuan")
    );

    private static Set<String> set(String... words) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : words) {
            String f = TextMatcher.fold(w);
            if (!f.isEmpty()) out.add(f);
        }
        return Set.copyOf(out);
    }

    /**
     * Variantes de la requête : originale repliée + synonymes des tokens / bigrammes connus.
     * Plafond pour éviter une explosion combinatoire.
     */
    public List<String> expand(String query) {
        String folded = TextMatcher.fold(query);
        if (folded.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(folded);
        out.add(query.trim());

        String[] tokens = folded.split(" ");
        for (String token : tokens) {
            if (token.length() < 3 || STOP.contains(token)) continue;
            out.add(token);
            for (Set<String> group : GROUPS) {
                if (groupContains(group, token)) {
                    out.addAll(group);
                }
            }
        }
        out.removeIf(s -> {
            String f = TextMatcher.fold(s);
            return f.length() < 3 || STOP.contains(f);
        });
        out.add(folded); // réinjecte la phrase complète même avec mots vides
        // Bigrammes (« data analyst », « product owner »)
        for (int i = 0; i + 1 < tokens.length; i++) {
            String bi = tokens[i] + " " + tokens[i + 1];
            for (Set<String> group : GROUPS) {
                if (group.contains(bi)) out.addAll(group);
            }
        }
        List<String> list = new ArrayList<>(out);
        return list.size() > 24 ? list.subList(0, 24) : list;
    }

    private static boolean groupContains(Set<String> group, String token) {
        if (group.contains(token)) return true;
        for (String g : group) {
            if (g.equals(token) || g.startsWith(token + " ") || g.endsWith(" " + token) || g.contains(" " + token + " ")) {
                return true;
            }
            // « dev » ↔ « developpeur » : préfixe court seulement si alias court dans le groupe
            if (token.length() >= 3 && (g.startsWith(token) || token.startsWith(g) && g.length() >= 3)) {
                return true;
            }
        }
        return false;
    }

    /** Indique si deux titres/entreprises sont quasi-doublons (déduplication soft). */
    public boolean nearDuplicate(String titleA, String companyA, String titleB, String companyB) {
        String ta = TextMatcher.compact(titleA);
        String tb = TextMatcher.compact(titleB);
        String ca = TextMatcher.compact(companyA);
        String cb = TextMatcher.compact(companyB);
        if (ta.isEmpty() || tb.isEmpty()) return false;
        boolean sameCompany = !ca.isEmpty() && ca.equals(cb);
        if (!sameCompany) return false;
        if (ta.equals(tb)) return true;
        int tol = TextMatcher.tolerance(Math.min(ta.length(), tb.length()));
        return TextMatcher.distance(ta, tb, Math.max(tol, 2)) <= Math.max(tol, 2);
    }
}
