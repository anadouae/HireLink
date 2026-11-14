package ma.ensat.hirelink.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.service.ParsedExternalJob;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse la Job Board API Greenhouse (GET /v1/boards/{token}/jobs?content=true).
 * Docs: https://docs.greenhouse.io/job-board.html
 * N'invente ni salaire, ni competences, ni applyUrl.
 */
public final class GreenhouseJobParser {

    public static final String SOURCE = "GREENHOUSE";
    private final ObjectMapper json = new ObjectMapper();

    public List<ParsedExternalJob> parse(String body, ExternalBoardDefinition board) {
        JsonNode root;
        try {
            root = json.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format Greenhouse inattendu");
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Format Greenhouse inattendu");
        JsonNode jobs = root.get("jobs");
        if (jobs == null || !jobs.isArray()) throw new IllegalArgumentException("Champ jobs Greenhouse manquant");
        List<ParsedExternalJob> out = new ArrayList<>();
        String companyFallback = board.companyName();
        for (JsonNode node : jobs) {
            if (node == null || !node.isObject()) continue;
            String id = text(node, "id");
            if (id.isBlank()) continue;
            String title = text(node, "title");
            String company = text(node, "company_name");
            if (company.isBlank()) company = companyFallback;
            String origin = https(text(node, "absolute_url"));
            if (title.isBlank() || company.isBlank() || origin == null) continue;
            String location = null;
            JsonNode loc = node.get("location");
            if (loc != null && loc.isObject()) location = blank(text(loc, "name"), 160);
            Instant published = parseInstant(text(node, "first_published"));
            if (published == null) published = parseInstant(text(node, "updated_at"));
            String description = ExternalHtmlText.toPlain(text(node, "content"), 4000);
            String externalId = clip(board.boardKey() + ":" + id, 160);
            out.add(new ParsedExternalJob(
                    externalId, clip(title, 200), clip(company, 200), origin, null,
                    location, null, description, null, null, null, published,
                    board.boardKey(), null));
        }
        return out;
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            try {
                return java.time.OffsetDateTime.parse(raw).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static String https(String url) {
        if (url == null) return null;
        String t = url.trim();
        if (!t.startsWith("https://") || t.contains(" ")) return null;
        return t.length() > 500 ? null : t;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return "";
        return v.isNumber() ? v.asText() : v.asText("");
    }

    private static String blank(String v, int max) {
        String c = clip(v, max);
        return c == null || c.isBlank() ? null : c;
    }

    private static String clip(String v, int max) {
        String c = v == null ? "" : v.trim();
        return c.length() > max ? c.substring(0, max) : c;
    }
}