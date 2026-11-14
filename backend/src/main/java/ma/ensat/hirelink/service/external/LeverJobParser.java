package ma.ensat.hirelink.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.service.ParsedExternalJob;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse Lever Postings API (GET /v0/postings/{site}?mode=json).
 * Docs: https://github.com/lever/postings-api
 * applyUrl conserve separement ; jamais invente depuis hostedUrl.
 */
public final class LeverJobParser {

    public static final String SOURCE = "LEVER";
    private final ObjectMapper json = new ObjectMapper();

    public List<ParsedExternalJob> parse(String body, ExternalBoardDefinition board) {
        JsonNode root;
        try {
            root = json.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format Lever inattendu");
        }
        if (root == null || !root.isArray()) throw new IllegalArgumentException("Format Lever inattendu");
        List<ParsedExternalJob> out = new ArrayList<>();
        for (JsonNode node : root) {
            if (node == null || !node.isObject()) continue;
            String id = text(node, "id");
            String title = text(node, "text");
            String origin = https(text(node, "hostedUrl"));
            String apply = https(text(node, "applyUrl"));
            if (id.isBlank() || title.isBlank() || origin == null) continue;
            String location = null;
            String commitment = null;
            JsonNode cats = node.get("categories");
            if (cats != null && cats.isObject()) {
                location = blank(text(cats, "location"), 160);
                commitment = blank(text(cats, "commitment"), 60);
            }
            String description = blank(text(node, "descriptionPlain"), 4000);
            if (description == null) description = ExternalHtmlText.toPlain(text(node, "description"), 4000);
            Instant published = null;
            JsonNode created = node.get("createdAt");
            if (created != null && created.isNumber()) {
                long ms = created.asLong();
                if (ms > 1_000_000_000_000L) published = Instant.ofEpochMilli(ms);
                else if (ms > 1_000_000_000L) published = Instant.ofEpochSecond(ms);
            }
            String externalId = clip(board.boardKey() + ":" + id, 160);
            out.add(new ParsedExternalJob(
                    externalId, clip(title, 200), clip(board.companyName(), 200), origin, apply,
                    location, commitment, description, null, null, null, published,
                    board.boardKey(), null));
        }
        return out;
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