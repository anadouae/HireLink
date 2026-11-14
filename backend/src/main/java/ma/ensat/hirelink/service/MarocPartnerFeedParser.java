package ma.ensat.hirelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Flux partenaires emploi Maroc (format HireLink v1).
 * Contrat : JSON {@code { "source":"MAROC_EMPLOI", "jobs":[ ... ] }} avec lien https d'origine obligatoire.
 */
public final class MarocPartnerFeedParser {

    public static final String SOURCE = ExternalSourceCatalog.MAROC_EMPLOI;

    private final ObjectMapper json = new ObjectMapper();

    public List<ParsedExternalJob> parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format partenaire inattendu");
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Format partenaire inattendu");
        JsonNode jobsNode = root.get("jobs");
        if (jobsNode == null || !jobsNode.isArray()) throw new IllegalArgumentException("Champ jobs manquant");
        List<ParsedExternalJob> out = new ArrayList<>();
        for (JsonNode node : jobsNode) {
            if (node == null || !node.isObject()) continue;
            String id = text(node, "id");
            String title = text(node, "title");
            String company = text(node, "company");
            String origin = https(text(node, "url"));
            if (id.isBlank() || title.isBlank() || company.isBlank() || origin == null) continue;
            String apply = https(text(node, "applyUrl"));
            out.add(new ParsedExternalJob(
                    clip(id, 80),
                    clip(title, 200),
                    clip(company, 200),
                    origin,
                    apply == null ? origin : apply,
                    blank(text(node, "location"), 160),
                    blank(text(node, "contract"), 60),
                    blank(text(node, "description"), 4000),
                    blank(text(node, "skills"), 500),
                    money(node, "salaryMin"),
                    money(node, "salaryMax"),
                    published(text(node, "publishedAt"))
            ));
        }
        return out;
    }

    private static Instant published(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static Integer money(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.canConvertToInt()) return null;
        int n = v.asInt(0);
        return n > 0 ? n : null;
    }

    private static String https(String url) {
        if (url == null) return null;
        String t = url.trim();
        if (!t.startsWith("https://") || t.contains(" ")) return null;
        return t.length() > 500 ? null : t;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String blank(String value, int max) {
        String c = clip(value, max);
        return c.isBlank() ? null : c;
    }

    private static String clip(String value, int max) {
        String clean = value == null ? "" : value.trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
