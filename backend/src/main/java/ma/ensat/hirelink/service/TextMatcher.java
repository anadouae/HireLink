package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

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

    /** Marques diacritiques retirées après normalisation NFD. */
    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    /** Tout sauf lettres, chiffres, + et # devient une espace (C++ / C# conservés). */
    private static final Pattern NON_TOKEN = Pattern.compile("[^\\p{L}\\p{N}+#]+");

    /** Minuscules, sans accents, ponctuation remplacée par une espace (seuls + et # sont conservés : C++, C#). */
    public static String fold(String value) {
        if (value == null) return "";
        String s = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        return NON_TOKEN.matcher(s).replaceAll(" ").trim();
    }

    /** Forme repliée sans espaces : « Node.js », « node js » et « nodejs » deviennent identiques. */
    public static String compact(String value) {
        return fold(value).replace(" ", "");
    }

    /**
     * Texte déjà normalisé ({@link #fold}), forme compacte et mots, pour réutiliser le repli
     * sans le recalculer à chaque token ou synonyme.
     */
    public static final class FoldedText {
        public final String folded;
        public final String compact;
        public final String[] words;

        private FoldedText(String folded, String compact, String[] words) {
            this.folded = folded;
            this.compact = compact;
            this.words = words;
        }

        /** Replie le texte brut une fois. */
        public static FoldedText ofRaw(String value) {
            return ofFolded(fold(value));
        }

        /**
         * Construit à partir d'une chaîne déjà obtenue par {@link #fold}.
         * {@code split(" ")} reprend le même découpage que l'ancien algorithme (chaîne vide → un mot vide).
         */
        public static FoldedText ofFolded(String folded) {
            String f = folded == null ? "" : folded;
            return new FoldedText(f, f.replace(" ", ""), f.split(" "));
        }
    }

    /**
     * Score de 0 à 1 de {@code query} par rapport à {@code text} : 1 égalité, ≈ 0,9 préfixe, 0,75 début de mot,
     * 0,6 sous-chaîne, puis 0,2 à 0,5 pour une faute de frappe. 0 si rien ne correspond.
     * Une saisie de plusieurs mots doit retrouver chacun de ses mots dans le texte (ordre libre).
     */
    public double score(String query, String text) {
        return scorePrepared(FoldedText.ofRaw(query), FoldedText.ofRaw(text));
    }

    /**
     * Même valeurs que {@link #score(String, String)} lorsque {@code query} et {@code text}
     * ont déjà été repliés (folded / compact / words).
     */
    public double scorePrepared(FoldedText query, FoldedText text) {
        if (query == null) query = FoldedText.ofFolded("");
        if (text == null) text = FoldedText.ofFolded("");
        double whole = singlePrepared(query.compact, text);
        if (query.words.length < 2) return whole;
        // AND strict : chaque mot significatif doit matcher. Pas de repli silencieux
        // sur un seul token (évite « stage pfe » → n'importe quel « Intern »).
        double sum = 0;
        int counted = 0;
        for (String w : query.words) {
            if (w.isBlank() || w.length() < 2) continue;
            double s = singlePrepared(w.replace(" ", ""), text);
            if (s == 0) {
                // La phrase compacte entière peut encore sauver (« fullstack » vs « full stack »).
                return whole;
            }
            sum += s;
            counted++;
        }
        if (counted == 0) return whole;
        return Math.max(whole, 0.95 * sum / counted);
    }

    private static double singlePrepared(String q, FoldedText text) {
        String t = text.compact;
        if (q.isEmpty() || t.isEmpty()) return 0;
        if (q.equals(t)) return 1;
        if (t.startsWith(q)) return 0.85 + 0.05 * q.length() / t.length();
        String[] words = text.words;
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
