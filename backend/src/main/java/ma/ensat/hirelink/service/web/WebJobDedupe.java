package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.TextMatcher;

/**
 * Clé de doublon entre sources : même intitulé + même entreprise + même lieu (repliés : accents, casse,
 * ponctuation, mentions « (H/F) » ou « remote »). Le premier vu est gardé : HireLink d'abord, puis l'ordre des sources.
 */
public final class WebJobDedupe {

    private WebJobDedupe() {}

    public static String key(String title, String company, String location) {
        String t = clean(title).replaceAll("\\b(h ?f|f ?h|m ?f ?d|m ?w ?d|f ?m ?d)\\b", " ");
        return squash(t) + "|" + squash(clean(company)
                .replaceAll("\\b(inc|ltd|llc|gmbh|sarl|sas|sa|corp|co)\\b", " ")) + "|" + squash(place(location));
    }

    private static String place(String location) {
        String l = clean(location);
        // « Remote », « Worldwide », « Anywhere » : même lieu « à distance ».
        if (l.isEmpty() || l.matches(".*\\b(remote|worldwide|anywhere|teletravail|a distance)\\b.*")) return "remote";
        return l;
    }

    private static String clean(String s) {
        return s == null ? "" : TextMatcher.fold(s).replaceAll("[^a-z0-9]+", " ");
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
