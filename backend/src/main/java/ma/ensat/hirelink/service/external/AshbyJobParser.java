package ma.ensat.hirelink.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.service.ParsedExternalJob;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse Ashby Job Postings API (GET /posting-api/job-board/{name}).
 * Docs: https://developers.ashbyhq.com/docs/public-job-posting-api
 * Respecte isListed=false (annonce non listee publiquement).
 */
public final class AshbyJobParser {

    public static final String SOURCE = "ASHBY";
    private final ObjectMapper json = new ObjectMapper();

    public List<ParsedExternalJob> parse(String body, ExternalBoardDefinition board) {
        JsonNode root;
        try {
            root = json.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format Ashby inattendu");
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Format Ashby inattendu");
        JsonNode jobs = root.get("jobs");
        if (jobs == null || !jobs.isArray()) throw new IllegalArgumentException("Champ jobs Ashby manquant");
        List<ParsedExternalJob> out = new ArrayList<>();
        for (JsonNode node : jobs) {
            if (node == null || !node.isObject()) continue;
            if (node.has("isListed") && node.get("isListed").isBoolean() && !node.get("isListed").asBoolean()) {
                continue;
            }
            String id = text(node, "id");
            if (id.isBlank()) id = text(node, "jobId");
            String title = text(node, "title");
            String origin = https(text(node, "jobUrl"));
            String apply = https(text(node, "applyUrl"));
            if (id.isBlank() || title.isBlank() || origin == null) continue;
            String location = blank(text(node, "location"), 160);
            String country = null;
            JsonNode address = node.get("address");
            if (address != null && address.isObject()) {
                JsonNode postal = address.get("postalAddress");
                if (postal != null && postal.isObject()) {
                    country = blank(text(postal, "addressCountry"), 80);
                    if (location == null) {
                        String city = text(postal, "addressLocality");
                        if (!city.isBlank()) location = blank(city, 160);
                    }
                }
            }
            String contract = mapEmployment(text(node, "employmentType"));
            String description = blank(text(node, "descriptionPlain"), 4000);
            if (description == null) description = ExternalHtmlText.toPlain(text(node, "descriptionHtml"), 4000);
            Instant published = parseInstant(text(node, "publishedAt"));
            String externalId = clip(board.boardKey() + ":" + id, 160);
            out.add(new ParsedExternalJob(
                    externalId, clip(title, 200), clip(board.companyName(), 200), origin, apply,
                    location, contract, description, null, null, null, published,
                    board.boardKey(), country));
        }
        return out;
    }

    private static String mapEmployment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw) {
            case "FullTime" -> "Full-time";
            case "PartTime" -> "Part-time";
            case "Intern" -> "Internship";
            case "Contract" -> "Contract";
            case "Temporary" -> "Temporary";
            default -> raw.length() > 60 ? raw.substring(0, 60) : raw;
        };
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            try {
                return java.time.OffsetDateTime.parse(raw).toInstant();
            } catch (Exception e2) {
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
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String blank(String v, int max) {
        String c = clip(v, max);
        return c.isBlank() ? null : c;
    }

    private static String clip(String v, int max) {
        String c = v == null ? "" : v.trim();
        return c.length() > max ? c.substring(0, max) : c;
    }
}