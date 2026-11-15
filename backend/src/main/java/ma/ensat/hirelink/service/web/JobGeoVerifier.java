package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.TextMatcher;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vérifie qu'une offre Web correspond au filtre pays/ville.
 * Si le pays demandé est précis et que la géo de l'offre est inconnue ou ambiguë → rejet
 * (jamais présenter un résultat non vérifié comme correspondant).
 */
@Component
public class JobGeoVerifier {

    private static final Map<String, Set<String>> TOKENS = new LinkedHashMap<>();

    static {
        tok("MA", "maroc", "morocco", "casablanca", "rabat", "tanger", "tangier", "marrakech", "fes", "fès",
                "agadir", "tetouan", "tétouan", "meknes", "oujda", "kenitra", "sale", "salé");
        tok("FR", "france", "paris", "lyon", "marseille", "toulouse", "lille", "bordeaux", "nantes", "nice",
                "strasbourg", "montpellier", "rennes", "grenoble");
        tok("DE", "germany", "deutschland", "allemagne", "berlin", "munich", "münchen", "hamburg", "frankfurt",
                "cologne", "köln", "stuttgart", "dusseldorf", "düseldorf", "rostock");
        tok("GB", "uk", "united kingdom", "great britain", "england", "scotland", "wales", "london", "manchester",
                "edinburgh", "birmingham", "bristol");
        tok("US", "usa", "united states", "america", "new york", "san francisco", "seattle", "boston", "austin",
                "chicago", "los angeles", "remote usa", "united states of america");
        tok("CA", "canada", "toronto", "montreal", "montréal", "vancouver", "ottawa", "quebec", "québec");
        tok("ES", "spain", "espagne", "madrid", "barcelona", "barcelone", "valencia", "seville", "séville");
        tok("BE", "belgium", "belgique", "brussels", "bruxelles", "antwerp", "anvers", "liege", "liège");
        tok("NL", "netherlands", "hollande", "pays-bas", "amsterdam", "rotterdam", "utrecht", "the hague");
        tok("CH", "switzerland", "suisse", "zurich", "zürich", "geneva", "genève", "lausanne", "basel", "berne");
        tok("IT", "italy", "italie", "rome", "milan", "milano", "turin", "naples", "napoli");
        tok("PT", "portugal", "lisbon", "lisbonne", "porto");
        tok("AE", "uae", "emirates", "dubai", "dubaï", "abu dhabi", "abou dabi");
        tok("TN", "tunisia", "tunisie", "tunis", "sfax");
        tok("SN", "senegal", "sénégal", "dakar");
        tok("CI", "ivory coast", "cote divoire", "côte d'ivoire", "abidjan");
        tok("IN", "india", "inde", "bangalore", "bengaluru", "mumbai", "hyderabad", "delhi", "pune");
        tok("BR", "brazil", "bresil", "brésil", "sao paulo", "são paulo", "rio");
        tok("AU", "australia", "australie", "sydney", "melbourne");
        tok("SG", "singapore", "singapour");
        tok("JP", "japan", "japon", "tokyo", "osaka");
        tok("PL", "poland", "pologne", "warsaw", "varsovie", "krakow", "cracovie");
        tok("AT", "austria", "autriche", "vienna", "vienne");
        tok("IE", "ireland", "irlande", "dublin");
        tok("SE", "sweden", "suede", "suède", "stockholm");
        tok("NO", "norway", "norvege", "norvège", "oslo");
        tok("DK", "denmark", "danemark", "copenhagen", "copenhague");
        tok("GR", "greece", "grece", "grèce", "athens", "athènes");
        tok("EE", "estonia", "estonie", "tallinn");
        tok("ZA", "south africa", "afrique du sud", "cape town", "johannesburg");
        tok("MX", "mexico", "mexique", "mexico city");
        tok("AR", "argentina", "argentine", "buenos aires");
        tok("CL", "chile", "chili", "santiago");
        tok("NG", "nigeria", "lagos", "abuja");
        tok("KE", "kenya", "nairobi");
        tok("EG", "egypt", "egypte", "égypte", "cairo", "le caire");
        tok("SA", "saudi", "arabie", "riyadh", "jeddah");
        tok("QA", "qatar", "doha");
        tok("KW", "kuwait", "koweit", "koweït");
        tok("TR", "turkey", "turquie", "istanbul", "ankara");
        tok("CN", "china", "chine", "beijing", "shanghai", "shenzhen");
        tok("KR", "korea", "corée", "seoul", "séoul");
        tok("NZ", "new zealand", "nouvelle-zelande", "auckland");
        tok("LU", "luxembourg");
    }

    private static void tok(String code, String... words) {
        TOKENS.put(code, Set.of(words));
    }

    /** true si aucun filtre pays (ALL / null). */
    public boolean isWorldwideFilter(String countryCode) {
        return countryCode == null || countryCode.isBlank()
                || "*".equals(countryCode) || "ALL".equalsIgnoreCase(countryCode)
                || "TOUS".equalsIgnoreCase(countryCode);
    }

    public boolean matches(DiscoveredJob job, String countryCode, String city) {
        if (job == null) return false;
        if (!isWorldwideFilter(countryCode)) {
            if (!countryMatches(job, countryCode.trim().toUpperCase(Locale.ROOT))) {
                return false;
            }
        }
        if (city != null && !city.isBlank()) {
            return cityMatches(job, city);
        }
        return true;
    }

    private boolean countryMatches(DiscoveredJob job, String want) {
        String code = job.countryCode() == null ? "" : job.countryCode().trim().toUpperCase(Locale.ROOT);
        if (!code.isEmpty() && code.equals(want)) return true;

        String blob = TextMatcher.fold(
                nullToEmpty(job.location()) + " " + nullToEmpty(job.city()) + " " + nullToEmpty(job.description()));
        if (blob.isEmpty()) return false; // géo inconnue → rejet si filtre pays

        // « Worldwide / Anywhere » : pas une preuve pour un pays précis
        if (isGlobalRemoteOnly(blob)) return false;

        Set<String> tokens = TOKENS.get(want);
        if (tokens == null) {
            // Pays non cartographié : exiger le code ISO explicite uniquement
            return false;
        }
        for (String t : tokens) {
            if (blob.contains(TextMatcher.fold(t))) return true;
        }
        return false;
    }

    private boolean cityMatches(DiscoveredJob job, String city) {
        String want = TextMatcher.fold(city);
        if (want.isEmpty()) return true;
        String blob = TextMatcher.fold(
                nullToEmpty(job.city()) + " " + nullToEmpty(job.location()) + " " + nullToEmpty(job.title()));
        return blob.contains(want);
    }

    private static boolean isGlobalRemoteOnly(String foldedBlob) {
        return foldedBlob.contains("worldwide")
                || foldedBlob.equals("remote")
                || foldedBlob.contains("anywhere")
                || foldedBlob.contains("work from anywhere")
                || foldedBlob.contains("global remote");
    }

    /** Devine un code pays depuis un libellé lieu ; null si ambigu. */
    public String inferCountryCode(String location) {
        if (location == null || location.isBlank()) return null;
        String fold = TextMatcher.fold(location);
        if (isGlobalRemoteOnly(fold)) return null;
        String found = null;
        for (Map.Entry<String, Set<String>> e : TOKENS.entrySet()) {
            for (String t : e.getValue()) {
                if (fold.contains(TextMatcher.fold(t))) {
                    if (found != null && !found.equals(e.getKey())) return null; // ambigu
                    found = e.getKey();
                    break;
                }
            }
        }
        return found;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
