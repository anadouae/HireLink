package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.ContractType;
import ma.ensat.hirelink.service.PlaceCatalogService;
import ma.ensat.hirelink.service.TextMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestions de métiers, lieux, nationalités, écoles, contrats, modes de travail et niveaux.
 * Classement par pertinence (insensible aux accents, tolérant aux fautes de frappe) via {@link TextMatcher} ;
 * ESCO (Commission européenne) complète les métiers. Les compétences sont servies par {@code /api/v1/skills}.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogSuggestController {

    private static final int LIMIT = 24;

    private static final List<String> JOBS = List.of(
            "Développeur Java junior", "Développeur Java confirmé", "Développeur full-stack",
            "Développeur frontend", "Développeur backend", "Développeur mobile", "Développeur Python",
            "Ingénieur logiciel", "Ingénieur DevOps", "Ingénieur data", "Ingénieur cybersécurité",
            "Data analyst", "Data engineer", "Data scientist", "Product manager", "Product owner",
            "UX designer", "UI designer", "QA engineer", "Testeur logiciel", "Scrum master",
            "Chef de projet digital", "Chef de projet SI", "Chargé de recrutement", "Responsable RH",
            "Business analyst", "Consultant SAP", "Administrateur systèmes", "Administrateur réseau",
            "Comptable", "Contrôleur de gestion", "Auditeur interne", "Commercial B2B",
            "Chargé de marketing digital", "Community manager", "Responsable communication",
            "Juriste d'entreprise", "Assistant juridique", "Avocat collaborateur",
            "Stagiaire développement", "Alternant data", "Lead développeur", "Architecte logiciel",
            "Technicien support", "Technicien de maintenance", "Responsable logistique",
            "Acheteur", "Assistant administratif", "Assistant de direction", "Secrétaire de direction",
            "Infirmier", "Aide-soignant", "Pharmacien", "Enseignant", "Formateur",
            "Graphiste", "Motion designer", "Rédacteur web", "Chargé de clientèle",
            "Conseiller bancaire", "Analyste crédit", "Responsable paie", "Gestionnaire de paie"
    );
    private static final List<String> SCHOOLS = List.of(
            "ENSA Tétouan", "ENSA Tanger", "ENSA Agadir", "ENSA Fès", "ENSA Kénitra", "ENSA Marrakech",
            "ENCG Casablanca", "ENCG Tanger", "ENCG Settat", "EMI", "ENSIAS", "EHTP", "INPT", "ENSEM",
            "ENSAM Casablanca", "FST Tanger", "FST Mohammedia", "FSJES", "ISCAE", "HEM", "ESCA", "UIR",
            "UM6P", "Al Akhawayn", "Université Hassan II", "Université Mohammed V", "EST Casablanca"
    );
    private static final List<String> CONTRACTS = ContractType.PREFERENCES;
    private static final List<String> MODES = List.of("Télétravail", "Sur site", "Hybride", "Indifférent");
    private static final List<String> DOMAINS = List.of(
            "Informatique", "Génie logiciel", "Réseaux et télécoms", "Cybersécurité", "Data / IA",
            "Génie civil", "Génie industriel", "Génie électrique", "Génie mécanique", "Génie des procédés",
            "Finance", "Comptabilité", "Marketing", "Commerce", "Management", "Ressources humaines",
            "Droit", "Économie", "Architecture", "Design", "Médecine", "Pharmacie", "Biologie",
            "Chimie", "Mathématiques", "Physique", "Langues", "Communication", "Logistique", "Autre"
    );


    /** {@code icon} : URL de logo (école / contrat) ; {@code flag} : emoji drapeau (lieu, nationalité, écoles MA). */
    public record Suggestion(String label, String flag, String country, String icon) {
        public Suggestion(String label, String flag, String country) {
            this(label, flag, country, "");
        }
    }

    /** Lieu du catalogue, avec son drapeau : le frontend s'en sert pour afficher un drapeau devant toute localisation. */
    public record PlaceDto(String city, String country, String flag) {}

    private static final Map<String, String> SCHOOL_DOMAINS = Map.ofEntries(
            Map.entry("ENSA Tétouan", "ensatetouan.ac.ma"),
            Map.entry("ENSA Tanger", "ensat.ac.ma"),
            Map.entry("ENSA Agadir", "ensa-agadir.ac.ma"),
            Map.entry("ENSA Fès", "ensaf.ac.ma"),
            Map.entry("ENSA Kénitra", "ensak.uit.ac.ma"),
            Map.entry("ENSA Marrakech", "ensa.ac.ma"),
            Map.entry("ENCG Casablanca", "encgcasa.ma"),
            Map.entry("ENCG Tanger", "encgt.ma"),
            Map.entry("ENCG Settat", "encg-settat.ma"),
            Map.entry("EMI", "emi.ac.ma"),
            Map.entry("ENSIAS", "ensias.um5.ac.ma"),
            Map.entry("EHTP", "ehtp.ac.ma"),
            Map.entry("INPT", "inpt.ac.ma"),
            Map.entry("ENSEM", "ensem.ac.ma"),
            Map.entry("ENSAM Casablanca", "ensam-casa.ma"),
            Map.entry("FST Tanger", "fstt.ac.ma"),
            Map.entry("FST Mohammedia", "fstm.ac.ma"),
            Map.entry("ISCAE", "iscae.ma"),
            Map.entry("HEM", "hem.ac.ma"),
            Map.entry("ESCA", "esca.ac.ma"),
            Map.entry("UIR", "uir.ac.ma"),
            Map.entry("UM6P", "um6p.ma"),
            Map.entry("Al Akhawayn", "aui.ma"),
            Map.entry("Université Hassan II", "univh2c.ma"),
            Map.entry("Université Mohammed V", "um5.ac.ma"),
            Map.entry("EST Casablanca", "est-uh2c.ac.ma")
    );

    private final TextMatcher matcher;
    private final PlaceCatalogService places;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public CatalogSuggestController(TextMatcher matcher, PlaceCatalogService places) {
        this.matcher = matcher;
        this.places = places;
    }

    @GetMapping("/suggest")
    public List<Suggestion> suggest(
            @RequestParam(defaultValue = "job") String kind,
            @RequestParam(defaultValue = "") String q
    ) {
        String query = q.trim();
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "place" -> places.search(query, LIMIT).stream()
                    .map(p -> new Suggestion(p.label(), p.flag(), p.city().isEmpty() ? "" : p.country())).toList();
            case "city" -> places.searchCities(query, LIMIT).stream()
                    .map(p -> new Suggestion(p.city(), p.flag(), p.country())).toList();
            case "nationality" -> places.nationalities(query, LIMIT).stream()
                    .map(n -> new Suggestion(n.label(), n.flag(), n.country())).toList();
            case "school" -> rankSchools(query);
            case "contract" -> rank(CONTRACTS, query);
            case "mode" -> rank(MODES, query);
            case "domain" -> rank(DOMAINS, query);
            default -> jobs(query);
        };
    }

    /** Tous les lieux du catalogue (une centaine) : mis en cache côté navigateur pour les drapeaux. */
    @GetMapping("/places")
    public List<PlaceDto> allPlaces() {
        return places.all().stream().map(p -> new PlaceDto(p.city(), p.country(), p.flag())).toList();
    }

    private List<Suggestion> jobs(String query) {
        List<Suggestion> out = rank(JOBS, query);
        if (query.length() >= 2 && out.size() < 8) {
            for (String title : esco(query)) {
                if (out.stream().noneMatch(s -> s.label().equalsIgnoreCase(title))) out.add(new Suggestion(title, "", ""));
            }
        }
        return out;
    }

    private List<Suggestion> rank(List<String> items, String query) {
        if (TextMatcher.fold(query).isEmpty()) return items.stream().limit(LIMIT).map(i -> new Suggestion(i, "", "")).toList();
        record Hit(String label, double score) {}
        return new ArrayList<>(items.stream()
                .map(i -> new Hit(i, matcher.score(query, i)))
                .filter(h -> h.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(LIMIT)
                .map(h -> new Suggestion(h.label(), "", ""))
                .toList());
    }

    private List<Suggestion> rankSchools(String query) {
        List<String> items = SCHOOLS;
        List<String> picked;
        if (TextMatcher.fold(query).isEmpty()) {
            picked = items.stream().limit(LIMIT).toList();
        } else {
            record Hit(String label, double score) {}
            picked = items.stream()
                    .map(i -> new Hit(i, matcher.score(query, i)))
                    .filter(h -> h.score() > 0)
                    .sorted(Comparator.comparingDouble(Hit::score).reversed())
                    .limit(LIMIT)
                    .map(Hit::label)
                    .toList();
        }
        return picked.stream().map(label -> {
            String domain = SCHOOL_DOMAINS.getOrDefault(label, "");
            String icon = domain.isBlank() ? "" : "https://www.google.com/s2/favicons?domain=" + domain + "&sz=64";
            return new Suggestion(label, "🇲🇦", "Maroc", icon);
        }).toList();
    }

    private List<String> esco(String query) {
        try {
            String url = "https://ec.europa.eu/esco/api/search?language=fr&type=occupation"
                    + "&limit=12&text=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return List.of();
            Matcher m = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(response.body());
            List<String> titles = new ArrayList<>();
            while (m.find() && titles.size() < 12) {
                String title = m.group(1).replace("\\\"", "\"").replace("\\u0027", "'").trim();
                if (!title.isBlank() && title.length() < 80) titles.add(title);
            }
            return titles;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
