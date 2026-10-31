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
                + "CH:Suisse,LU:Luxembourg,CA:Canada,US:États-Unis,AE:Émirats arabes unis,SA:Arabie saoudite,QA:Qatar,TR:Turquie,"
                + "TN:Tunisie,DZ:Algérie,EG:Égypte,SN:Sénégal,CI:Côte d'Ivoire,ML:Mali,MR:Mauritanie").split(",")) {
            COUNTRIES.put(c.substring(0, 2), c.substring(3));
        }
        cities("MA", "Casablanca,Rabat,Tanger,Tétouan,Fès,Marrakech,Agadir,Oujda,Meknès,Kénitra,Témara,Salé,El Jadida,Safi,Nador,"
                + "Mohammedia,Béni Mellal,Khouribga,Settat,Berrechid,Larache,Ksar El Kébir,Taza,Essaouira,Dakhla,Laâyoune,Guelmim,"
                + "Errachidia,Ouarzazate,Ifrane,Al Hoceïma,Tiznit,Taroudant,Chefchaouen,Martil,M'diq,Fnideq,Asilah,Khémisset,Berkane,"
                + "Tanger Med,Benguerir,Skhirat,Bouznika,Nouaceur");
        cities("FR", "Paris,Lyon,Marseille,Toulouse,Lille,Bordeaux,Nantes");
        cities("ES", "Madrid,Barcelone,Séville,Malaga");
        cities("PT", "Lisbonne,Porto");
        cities("BE", "Bruxelles");
        cities("GB", "Londres");
        cities("DE", "Berlin,Munich");
        cities("IT", "Rome,Milan");
        cities("NL", "Amsterdam");
        cities("CH", "Genève,Zurich");
        cities("LU", "Luxembourg");
        cities("CA", "Montréal,Toronto");
        cities("US", "New York");
        cities("AE", "Dubaï,Abou Dabi");
        cities("SA", "Riyad");
        cities("QA", "Doha");
        cities("TR", "Istanbul");
        cities("TN", "Tunis");
        cities("DZ", "Alger");
        cities("EG", "Le Caire");
        cities("SN", "Dakar");
        cities("CI", "Abidjan");
        COUNTRIES.forEach((code, name) -> PLACES.add(new Place("", name, code, flag(code))));
        for (String n : ("MA:Marocaine,FR:Française,ES:Espagnole,PT:Portugaise,BE:Belge,GB:Britannique,DE:Allemande,IT:Italienne,"
                + "NL:Néerlandaise,CH:Suisse,CA:Canadienne,US:Américaine,AE:Émiratie,TN:Tunisienne,DZ:Algérienne,SN:Sénégalaise,"
                + "EG:Égyptienne,CI:Ivoirienne,ML:Malienne,MR:Mauritanienne,TR:Turque,SA:Saoudienne").split(",")) {
            String code = n.substring(0, 2);
            NATIONALITIES.add(new Nationality(n.substring(3), COUNTRIES.get(code), flag(code)));
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
