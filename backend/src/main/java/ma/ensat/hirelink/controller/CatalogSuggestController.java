package ma.ensat.hirelink.controller;

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
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestions de métiers et de compétences.
 * ESCO (Commission européenne) est interrogé sans clé ; un lexique local complète la réponse.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogSuggestController {

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
    private static final List<String> SKILLS = List.of(
            "Java", "Spring Boot", "Python", "SQL", "JavaScript", "TypeScript", "React", "Angular",
            "Vue.js", "Node.js", "PHP", "Laravel", "C#", ".NET", "Kotlin", "Swift", "Flutter",
            "Excel", "Power BI", "Tableau", "Figma", "Adobe Photoshop", "Docker", "Kubernetes",
            "Git", "Linux", "AWS", "Azure", "GCP", "Communication", "Gestion de projet",
            "Recrutement", "Négociation", "Comptabilité", "SEO", "Scrum", "Agile", "Anglais",
            "Français", "Arabe", "Espagnol", "Salesforce", "SAP", "PowerPoint", "Word",
            "Analyse de données", "Machine learning", "Cybersécurité", "Rédaction", "Paie",
            "Droit du travail", "Service client", "Vente", "Logistique"
    );
    public record Suggestion(String label, String flag, String country) {}

    private static final List<Suggestion> CITIES = List.of(
            city("Casablanca", "Maroc", "🇲🇦"), city("Rabat", "Maroc", "🇲🇦"), city("Tanger", "Maroc", "🇲🇦"),
            city("Tétouan", "Maroc", "🇲🇦"), city("Fès", "Maroc", "🇲🇦"), city("Marrakech", "Maroc", "🇲🇦"),
            city("Agadir", "Maroc", "🇲🇦"), city("Oujda", "Maroc", "🇲🇦"), city("Meknès", "Maroc", "🇲🇦"),
            city("Kénitra", "Maroc", "🇲🇦"), city("Témara", "Maroc", "🇲🇦"), city("Salé", "Maroc", "🇲🇦"),
            city("El Jadida", "Maroc", "🇲🇦"), city("Safi", "Maroc", "🇲🇦"), city("Nador", "Maroc", "🇲🇦"),
            city("Mohammedia", "Maroc", "🇲🇦"), city("Béni Mellal", "Maroc", "🇲🇦"), city("Khouribga", "Maroc", "🇲🇦"),
            city("Settat", "Maroc", "🇲🇦"), city("Berrechid", "Maroc", "🇲🇦"), city("Larache", "Maroc", "🇲🇦"),
            city("Ksar El Kébir", "Maroc", "🇲🇦"), city("Taza", "Maroc", "🇲🇦"), city("Essaouira", "Maroc", "🇲🇦"),
            city("Dakhla", "Maroc", "🇲🇦"), city("Laâyoune", "Maroc", "🇲🇦"), city("Guelmim", "Maroc", "🇲🇦"),
            city("Errachidia", "Maroc", "🇲🇦"), city("Ouarzazate", "Maroc", "🇲🇦"), city("Ifrane", "Maroc", "🇲🇦"),
            city("Al Hoceïma", "Maroc", "🇲🇦"), city("Tiznit", "Maroc", "🇲🇦"), city("Taroudant", "Maroc", "🇲🇦"),
            city("Paris", "France", "🇫🇷"), city("Lyon", "France", "🇫🇷"), city("Marseille", "France", "🇫🇷"),
            city("Madrid", "Espagne", "🇪🇸"), city("Barcelone", "Espagne", "🇪🇸"),
            city("Lisbonne", "Portugal", "🇵🇹"), city("Bruxelles", "Belgique", "🇧🇪"),
            city("Londres", "Royaume-Uni", "🇬🇧"), city("Berlin", "Allemagne", "🇩🇪"),
            city("Rome", "Italie", "🇮🇹"), city("Amsterdam", "Pays-Bas", "🇳🇱"),
            city("Genève", "Suisse", "🇨🇭"), city("Montréal", "Canada", "🇨🇦"),
            city("New York", "États-Unis", "🇺🇸"), city("Dubaï", "Émirats arabes unis", "🇦🇪"),
            city("Tunis", "Tunisie", "🇹🇳"), city("Alger", "Algérie", "🇩🇿"), city("Dakar", "Sénégal", "🇸🇳"),
            city("Le Caire", "Égypte", "🇪🇬")
    );
    private static final List<Suggestion> NATIONALITIES = List.of(
            city("Marocaine", "Maroc", "🇲🇦"), city("Française", "France", "🇫🇷"),
            city("Espagnole", "Espagne", "🇪🇸"), city("Portugaise", "Portugal", "🇵🇹"),
            city("Belge", "Belgique", "🇧🇪"), city("Britannique", "Royaume-Uni", "🇬🇧"),
            city("Allemande", "Allemagne", "🇩🇪"), city("Italienne", "Italie", "🇮🇹"),
            city("Néerlandaise", "Pays-Bas", "🇳🇱"), city("Suisse", "Suisse", "🇨🇭"),
            city("Canadienne", "Canada", "🇨🇦"), city("Américaine", "États-Unis", "🇺🇸"),
            city("Émiratie", "Émirats arabes unis", "🇦🇪"), city("Tunisienne", "Tunisie", "🇹🇳"),
            city("Algérienne", "Algérie", "🇩🇿"), city("Sénégalaise", "Sénégal", "🇸🇳"),
            city("Égyptienne", "Égypte", "🇪🇬"), city("Ivoirienne", "Côte d'Ivoire", "🇨🇮"),
            city("Malienne", "Mali", "🇲🇱"), city("Mauritanienne", "Mauritanie", "🇲🇷"),
            city("Libanaise", "Liban", "🇱🇧"), city("Turque", "Turquie", "🇹🇷"),
            city("Indienne", "Inde", "🇮🇳"), city("Chinoise", "Chine", "🇨🇳")
    );
    private static final List<String> SCHOOLS = List.of(
            "ENSA Tétouan", "ENSA Tanger", "ENSA Agadir", "ENSA Fès", "ENSA Kénitra", "ENSA Marrakech",
            "ENCG Casablanca", "ENCG Tanger", "ENCG Settat", "EMI", "ENSIAS", "EHTP", "INPT", "ENSEM",
            "ENSAM Casablanca", "FST Tanger", "FST Mohammedia", "FSJES", "ISCAE", "HEM", "ESCA", "UIR",
            "UM6P", "Al Akhawayn", "Université Hassan II", "Université Mohammed V", "EST Casablanca"
    );
    private static final List<String> CONTRACTS = List.of(
            "Stage", "CDI", "CDD", "Alternance", "Freelance", "Premier emploi", "Job étudiant", "Bénévolat"
    );
    private static final List<String> MODES = List.of("Télétravail", "Sur site", "Hybride", "Indifférent");
    private static final List<String> LEVELS = List.of(
            "Bac", "Bac+2", "Bac+3", "Bac+4", "Bac+5", "Doctorat", "Stagiaire", "Junior", "Confirmé", "Senior"
    );

    private static Suggestion city(String name, String country, String flag) {
        return new Suggestion(name, flag, country);
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @GetMapping("/suggest")
    public List<Suggestion> suggest(
            @RequestParam(defaultValue = "job") String kind,
            @RequestParam(defaultValue = "") String q
    ) {
        String query = q == null ? "" : q.trim();
        String mode = kind == null ? "job" : kind.toLowerCase(Locale.ROOT);
        List<Suggestion> out = new ArrayList<>();
        if ("place".equals(mode)) {
            if (query.length() >= 2) out.addAll(places(query));
            String needle = fold(query);
            for (Suggestion city : CITIES) {
                if (needle.isEmpty() || fold(city.label()).contains(needle) || fold(city.country()).contains(needle)) {
                    add(out, city);
                }
                if (out.size() >= 24) break;
            }
            return out;
        }
        if ("nationality".equals(mode)) {
            String needle = fold(query);
            for (Suggestion item : NATIONALITIES) {
                if (needle.isEmpty() || fold(item.label()).contains(needle) || fold(item.country()).contains(needle)) {
                    add(out, item);
                }
                if (out.size() >= 24) break;
            }
            return out;
        }
        List<String> local = switch (mode) {
            case "skill" -> SKILLS;
            case "school" -> SCHOOLS;
            case "contract" -> CONTRACTS;
            case "mode" -> MODES;
            case "level" -> LEVELS;
            default -> JOBS;
        };
        if (query.length() >= 2 && ("job".equals(mode) || "skill".equals(mode))) {
            for (String title : esco(mode, query)) add(out, new Suggestion(title, "", ""));
        }
        String needle = fold(query);
        for (String item : local) {
            if (needle.isEmpty() || fold(item).contains(needle)) add(out, new Suggestion(item, "", ""));
            if (out.size() >= 24) break;
        }
        return out;
    }

    private static void add(List<Suggestion> out, Suggestion item) {
        for (Suggestion existing : out) {
            if (existing.label().equalsIgnoreCase(item.label())) return;
        }
        out.add(item);
    }

    private static String fold(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private List<Suggestion> places(String query) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .header("User-Agent", "HireLink/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return List.of();
            Matcher matcher = Pattern.compile("\"display_name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(response.body());
            List<Suggestion> names = new ArrayList<>();
            while (matcher.find() && names.size() < 8) {
                String raw = matcher.group(1).replace("\\\"", "\"").trim();
                String[] parts = raw.split(",");
                String city = parts[0].trim();
                String country = parts.length > 1 ? parts[parts.length - 1].trim() : "";
                if (!city.isBlank() && city.length() < 48) names.add(new Suggestion(city, flagFor(country), country));
            }
            return names;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String flagFor(String country) {
        String key = fold(country);
        return switch (key) {
            case "maroc", "morocco" -> "🇲🇦";
            case "france" -> "🇫🇷";
            case "espagne", "spain" -> "🇪🇸";
            case "portugal" -> "🇵🇹";
            case "belgique", "belgium" -> "🇧🇪";
            case "royaume-uni", "united kingdom" -> "🇬🇧";
            case "allemagne", "germany" -> "🇩🇪";
            case "italie", "italy" -> "🇮🇹";
            case "pays-bas", "netherlands" -> "🇳🇱";
            case "suisse", "switzerland" -> "🇨🇭";
            case "canada" -> "🇨🇦";
            case "etats-unis", "united states" -> "🇺🇸";
            case "emirats arabes unis", "united arab emirates" -> "🇦🇪";
            case "tunisie", "tunisia" -> "🇹🇳";
            case "algerie", "algeria" -> "🇩🇿";
            case "senegal" -> "🇸🇳";
            case "egypte", "egypt" -> "🇪🇬";
            default -> "";
        };
    }

    private List<String> esco(String mode, String query) {
        try {
            String type = "skill".equals(mode) ? "skill" : "occupation";
            String url = "https://ec.europa.eu/esco/api/search?language=fr&type=" + type
                    + "&limit=12&text=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return List.of();
            Matcher matcher = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(response.body());
            List<String> titles = new ArrayList<>();
            while (matcher.find() && titles.size() < 12) {
                String title = matcher.group(1).replace("\\\"", "\"").replace("\\u0027", "'").trim();
                if (!title.isBlank() && title.length() < 80) titles.add(title);
            }
            return titles;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
