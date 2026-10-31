package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalisation et correspondance floue partagées par toutes les recherches du serveur
 * (compétences, villes, métiers, écoles, profil candidat ↔ offre).
 * Insensible à la casse et aux accents ; tolère les fautes de frappe (distance de Damerau-Levenshtein).
 * Le module frontend {@code search.js} applique la même échelle de scores.
 */
@Service
public class TextMatcher {

    /** Score minimal d'une correspondance « directe » (égalité, préfixe, début de mot ou sous-chaîne). */
    public static final double DIRECT = 0.6;

    /** Minuscules, sans accents, ponctuation remplacée par une espace (seuls + et # sont conservés : C++, C#). */
    public static String fold(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return s.replaceAll("[^\\p{L}\\p{N}+#]+", " ").trim();
    }

    /** Forme repliée sans espaces : « Node.js », « node js » et « nodejs » deviennent identiques. */
    public static String compact(String value) {
        return fold(value).replace(" ", "");
    }

    /**
     * Score de 0 à 1 de {@code query} par rapport à {@code text} : 1 égalité, ≈ 0,9 préfixe, 0,75 début de mot,
     * 0,6 sous-chaîne, puis 0,2 à 0,5 pour une faute de frappe. 0 si rien ne correspond.
     * Une saisie de plusieurs mots doit retrouver chacun de ses mots dans le texte (ordre libre).
     */
    public double score(String query, String text) {
        String t = compact(text);
        double whole = single(compact(query), t, text);
        String[] words = fold(query).split(" ");
        if (words.length < 2) return whole;
        double sum = 0;
        for (String w : words) {
            double s = single(w.replace(" ", ""), t, text);
            if (s == 0) return whole;
            sum += s;
        }
        return Math.max(whole, 0.95 * sum / words.length);
    }

    private static double single(String q, String t, String text) {
        if (q.isEmpty() || t.isEmpty()) return 0;
        if (q.equals(t)) return 1;
        if (t.startsWith(q)) return 0.85 + 0.05 * q.length() / t.length();
        String[] words = fold(text).split(" ");
        for (String word : words) {
            if (word.startsWith(q)) return 0.75;
        }
        if (q.length() >= 2 && t.contains(q)) return 0.6;
        int tolerance = tolerance(q.length());
        if (tolerance == 0) return 0;
        int best = distance(q, t, tolerance);
        for (int len = q.length() - 1; len <= q.length() + 1 && len <= t.length(); len++) {
            best = Math.min(best, distance(q, t.substring(0, len), tolerance)); // saisie partielle avec faute
        }
        for (String word : words) {
            best = Math.min(best, distance(q, word, tolerance));
        }
        return best <= tolerance ? 0.5 - 0.1 * best : 0;
    }

    /** Vrai si {@code query} correspond directement (sans faute de frappe) à {@code text}. */
    public boolean contains(String query, String text) {
        return score(query, text) >= DIRECT;
    }

    /** Nombre de fautes tolérées selon la longueur de la saisie. */
    static int tolerance(int length) {
        if (length < 4) return 0;
        return length < 6 ? 1 : 2;
    }

    /** Distance de Damerau-Levenshtein (transposition comptée pour 1) ; renvoie max+1 au-delà de {@code max}. */
    static int distance(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
