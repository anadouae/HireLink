package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Référentiel local des lieux : villes marocaines d'abord, puis grandes villes internationales et pays.
 * Le drapeau est calculé depuis le code pays ISO (émoji drapeau, aucune image ni service externe).
 */
@Service
public class PlaceCatalogService {

    /** {@code city} vide pour une entrée « pays seul ». */
    public record Place(String city, String country, String code, String flag) {
        public String label() { return city.isEmpty() ? country : city; }
    }

    public record Nationality(String label, String country, String flag) {}

    private static final Map<String, String> COUNTRIES = new LinkedHashMap<>();
    private static final List<Place> PLACES = new ArrayList<>();
    private static final List<Nationality> NATIONALITIES = new ArrayList<>();

    static {
        for (String c : ("MA:Maroc,FR:France,ES:Espagne,PT:Portugal,BE:Belgique,GB:Royaume-Uni,DE:Allemagne,IT:Italie,NL:Pays-Bas,"
                + "CH:Suisse,LU:Luxembourg,IE:Irlande,AT:Autriche,SE:Suède,NO:Norvège,DK:Danemark,PL:Pologne,CZ:Tchéquie,"
                + "CA:Canada,US:États-Unis,MX:Mexique,BR:Brésil,AR:Argentine,CL:Chili,"
                + "AE:Émirats arabes unis,SA:Arabie saoudite,QA:Qatar,KW:Koweït,BH:Bahreïn,OM:Oman,TR:Turquie,IL:Israël,"
                + "TN:Tunisie,DZ:Algérie,EG:Égypte,SN:Sénégal,CI:Côte d'Ivoire,ML:Mali,MR:Mauritanie,MA:Maroc,"
                + "CN:Chine,JP:Japon,KR:Corée du Sud,IN:Inde,SG:Singapour,HK:Hong Kong,AU:Australie,NZ:Nouvelle-Zélande,"
                + "ZA:Afrique du Sud,NG:Nigeria,KE:Kenya,GH:Ghana").split(",")) {
            COUNTRIES.putIfAbsent(c.substring(0, 2), c.substring(3));
        }
        cities("MA", "Casablanca,Rabat,Tanger,Tétouan,Fès,Marrakech,Agadir,Oujda,Meknès,Kénitra,Témara,Salé,El Jadida,Safi,Nador,"
                + "Mohammedia,Béni Mellal,Khouribga,Settat,Berrechid,Larache,Ksar El Kébir,Taza,Essaouira,Dakhla,Laâyoune,Guelmim,"
                + "Errachidia,Ouarzazate,Ifrane,Al Hoceïma,Tiznit,Taroudant,Chefchaouen,Martil,M'diq,Fnideq,Asilah,Khémisset,Berkane,"
                + "Tanger Med,Benguerir,Skhirat,Bouznika,Nouaceur,Khemisset,Sidi Slimane,Youssoufia,Midelt,Azrou");
        cities("FR", "Paris,Lyon,Marseille,Toulouse,Lille,Bordeaux,Nantes,Nice,Strasbourg,Rennes,Montpellier,Grenoble");
        cities("ES", "Madrid,Barcelone,Séville,Malaga,Valence,Bilbao");
        cities("PT", "Lisbonne,Porto,Faro");
        cities("BE", "Bruxelles,Anvers,Liège,Gand");
        cities("GB", "Londres,Manchester,Édimbourg,Birmingham");
        cities("IE", "Dublin");
        cities("DE", "Berlin,Munich,Francfort,Hambourg,Cologne");
        cities("IT", "Rome,Milan,Turin,Naples");
        cities("NL", "Amsterdam,Rotterdam,La Haye");
        cities("CH", "Genève,Zurich,Lausanne,Bâle");
        cities("LU", "Luxembourg");
        cities("AT", "Vienne");
        cities("SE", "Stockholm");
        cities("NO", "Oslo");
        cities("DK", "Copenhague");
        cities("PL", "Varsovie,Cracovie");
        cities("CA", "Montréal,Toronto,Vancouver,Ottawa,Québec");
        cities("US", "New York,San Francisco,Boston,Seattle,Chicago,Austin,Los Angeles,Washington");
        cities("MX", "Mexico");
        cities("BR", "São Paulo,Rio de Janeiro");
        cities("AR", "Buenos Aires");
        cities("CL", "Santiago");
        cities("AE", "Dubaï,Abou Dabi,Charjah");
        cities("SA", "Riyad,Djeddah");
        cities("QA", "Doha");
        cities("KW", "Koweït");
        cities("BH", "Manama");
        cities("OM", "Mascate");
        cities("TR", "Istanbul,Ankara");
        cities("TN", "Tunis,Sfax");
        cities("DZ", "Alger,Oran");
        cities("EG", "Le Caire,Alexandrie");
        cities("SN", "Dakar");
        cities("CI", "Abidjan");
        cities("NG", "Lagos,Abuja");
        cities("KE", "Nairobi");
        cities("GH", "Accra");
        cities("ZA", "Le Cap,Johannesburg");
        cities("CN", "Pékin,Shanghai,Shenzhen");
        cities("JP", "Tokyo,Osaka");
        cities("KR", "Séoul");
        cities("IN", "Bangalore,Mumbai,Hyderabad,New Delhi");
        cities("SG", "Singapour");
        cities("HK", "Hong Kong");
        cities("AU", "Sydney,Melbourne");
        cities("NZ", "Auckland");
        COUNTRIES.forEach((code, name) -> PLACES.add(new Place("", name, code, flag(code))));
        for (String n : ("MA:Marocaine,FR:Française,ES:Espagnole,PT:Portugaise,BE:Belge,GB:Britannique,DE:Allemande,IT:Italienne,"
                + "NL:Néerlandaise,CH:Suisse,CA:Canadienne,US:Américaine,AE:Émiratie,TN:Tunisienne,DZ:Algérienne,SN:Sénégalaise,"
                + "EG:Égyptienne,CI:Ivoirienne,ML:Malienne,MR:Mauritanienne,TR:Turque,SA:Saoudienne,BR:Brésilienne,IN:Indienne,"
                + "CN:Chinoise,JP:Japonaise,KR:Sud-coréenne,AU:Australienne,ZA:Sud-africaine,NG:Nigériane,SN:Sénégalaise").split(",")) {
            String code = n.substring(0, 2);
            if (COUNTRIES.containsKey(code)) {
                NATIONALITIES.add(new Nationality(n.substring(3), COUNTRIES.get(code), flag(code)));
            }
        }
    }

    private final TextMatcher matcher;

    public PlaceCatalogService(TextMatcher matcher) {
        this.matcher = matcher;
    }

    private static void cities(String code, String names) {
        for (String name : names.split(",")) PLACES.add(new Place(name, COUNTRIES.get(code), code, flag(code)));
    }

    /** Émoji drapeau d'un code pays ISO (deux indicateurs régionaux Unicode). */
    static String flag(String code) {
        return new StringBuilder()
                .appendCodePoint(0x1F1E6 + code.charAt(0) - 'A')
                .appendCodePoint(0x1F1E6 + code.charAt(1) - 'A')
                .toString();
    }

    public List<Place> all() {
        return PLACES;
    }

    /** Lieux triés par pertinence ; à pertinence égale, le Maroc passe en premier. Sans saisie : villes marocaines. */
    public List<Place> search(String query, int limit) {
        if (TextMatcher.fold(query).isEmpty()) return PLACES.stream().limit(limit).toList();
        record Hit(Place place, double score) {}
        return PLACES.stream()
                .map(p -> new Hit(p, Math.max(matcher.score(query, p.label()), 0.5 * matcher.score(query, p.country()))))
                .filter(h -> h.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed()
                        .thenComparing(h -> !"MA".equals(h.place().code())))
                .limit(limit)
                .map(Hit::place)
                .toList();
    }

    /**
     * Villes seules, sans les entrées-pays : le lieu d'une offre doit rester une ville.
     * Le pays se déduit ensuite, il n'est jamais saisi à la place.
     */
    public List<Place> searchCities(String query, int limit) {
        List<Place> cities = PLACES.stream().filter(p -> !p.city().isEmpty()).toList();
        if (TextMatcher.fold(query).isEmpty()) return cities.stream().limit(limit).toList();
        record Hit(Place place, double score) {}
        return cities.stream()
                .map(p -> new Hit(p, matcher.score(query, p.city())))
                .filter(h -> h.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed()
                        .thenComparing(h -> !"MA".equals(h.place().code())))
                .limit(limit)
                .map(Hit::place)
                .toList();
    }

    /** Le pays d'un lieu connu ; vide si le catalogue ne le reconnaît pas. */
    public String countryOf(String label) {
        String needle = TextMatcher.fold(label == null ? "" : label.trim());
        if (needle.isEmpty()) return "";
        for (Place p : PLACES) {
            if (!p.city().isEmpty() && TextMatcher.fold(p.city()).equals(needle)) return p.country();
        }
        return "";
    }

    /**
     * Ville canonique du catalogue (ex. Tetouan → Tétouan) pour filtres accent-insensibles.
     * Si inconnue, renvoie le libellé trimé tel quel.
     */
    public String canonicalCity(String label) {
        if (label == null) return null;
        String raw = label.trim();
        if (raw.isEmpty()) return null;
        String needle = TextMatcher.fold(raw);
        for (Place p : PLACES) {
            if (!p.city().isEmpty() && TextMatcher.fold(p.city()).equals(needle)) return p.city();
        }
        return raw;
    }

    public boolean isCountry(String label) {
        String needle = TextMatcher.fold(label == null ? "" : label.trim());
        if (needle.isEmpty()) return false;
        return COUNTRIES.values().stream().anyMatch(c -> TextMatcher.fold(c).equals(needle));
    }

    public List<Nationality> nationalities(String query, int limit) {
        if (TextMatcher.fold(query).isEmpty()) return NATIONALITIES.stream().limit(limit).toList();
        record Hit(Nationality item, double score) {}
        return NATIONALITIES.stream()
                .map(n -> new Hit(n, Math.max(matcher.score(query, n.label()), 0.5 * matcher.score(query, n.country()))))
                .filter(h -> h.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(limit)
                .map(Hit::item)
                .toList();
    }
}
