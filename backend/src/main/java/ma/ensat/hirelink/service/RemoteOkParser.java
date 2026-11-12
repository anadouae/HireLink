package ma.ensat.hirelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lit le JSON public de Remote OK. Le premier objet porte les conditions d'attribution, pas une offre.
 * Un champ absent reste absent.
 */
public final class RemoteOkParser {

    public static final String SOURCE = "REMOTE_OK";
    public static final String SOURCE_LABEL = "Remote OK";
    public static final String SOURCE_HOME = "https://remoteok.com";

    public record ParsedJob(
            String externalId,
            String title,
            String company,
            String originUrl,
            String applyUrl,
            String location,
            String contractLabel,
            String description,
            String skills,
            Integer salaryMin,
            Integer salaryMax,
            Instant publishedAt
    ) {}

    private static final Set<String> CONTRACTS = Set.of("full-time", "full time", "part-time", "part time", "contract", "internship", "freelance");
    private static final Set<String> SKIP_TAGS = Set.of("remote", "worldwide", "senior", "junior", "mid", "entry");
    private final ObjectMapper json = new ObjectMapper();

    public List<ParsedJob> parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format inattendu");
        }
        if (root == null || !root.isArray()) throw new IllegalArgumentException("Format inattendu");
        List<ParsedJob> jobs = new ArrayList<>();
        for (JsonNode node : root) {
            if (node == null || !node.isObject() || node.hasNonNull("legal") || !node.hasNonNull("id")) continue;
            String id = node.get("id").asText("").trim();
            String title = text(node, "position");
            if (title.isBlank()) title = text(node, "title");
            String company = text(node, "company");
            String origin = https(text(node, "url"));
            if (id.isBlank() || title.isBlank() || company.isBlank() || origin == null) continue;
            String apply = https(text(node, "apply_url"));
            jobs.add(new ParsedJob(
                    id,
                    clip(title, 200),
                    clip(company, 200),
                    origin,
                    apply == null ? origin : apply,
                    blankToNull(text(node, "location"), 160),
                    contract(node),
                    blankToNull(stripHtml(text(node, "description")), 4000),
                    skills(node),
                    money(node, "salary_min"),
                    money(node, "salary_max"),
                    published(node.get("date"))
            ));
        }
        return jobs;
    }

    private static String contract(JsonNode node) {
        for (JsonNode tag : tags(node)) {
            String value = tag.asText("").trim();
            if (CONTRACTS.contains(value.toLowerCase(Locale.ROOT))) return clip(value, 60);
        }
        return null;
    }

    private static String skills(JsonNode node) {
        List<String> kept = new ArrayList<>();
        for (JsonNode tag : tags(node)) {
            String value = tag.asText("").trim();
            if (value.isBlank()) continue;
            String key = value.toLowerCase(Locale.ROOT);
            if (CONTRACTS.contains(key) || SKIP_TAGS.contains(key)) continue;
            if (!kept.contains(value)) kept.add(value);
        }
        if (kept.isEmpty()) return null;
        return clip(String.join(", ", kept), 500);
    }

    private static List<JsonNode> tags(JsonNode node) {
        JsonNode tags = node.get("tags");
        List<JsonNode> out = new ArrayList<>();
        if (tags != null && tags.isArray()) tags.forEach(out::add);
        return out;
    }

    private static Instant published(JsonNode date) {
        if (date == null || date.isNull()) return null;
        String raw = date.asText("").trim();
        if (raw.isBlank()) return null;
        try {
            long value = (long) Double.parseDouble(raw);
            if (value > 1_000_000_000_000L) value = value / 1000L;
            if (value < 946684800L || value > 4102444800L) return null;
            return Instant.ofEpochSecond(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer money(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        int amount = value.asInt(0);
        return amount > 0 ? amount : null;
    }

    private static String https(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (!trimmed.startsWith("https://") || trimmed.contains(" ")) return null;
        return trimmed.length() > 500 ? null : trimmed;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static final java.util.regex.Pattern ENTITY = java.util.regex.Pattern.compile("&#x([0-9a-fA-F]+);|&#([0-9]+);");

    private static String stripHtml(String html) {
        if (html == null) return "";
        String text = removeMarkup(html);
        text = ENTITY.matcher(text).replaceAll(RemoteOkParser::entity);
        text = text.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        return removeMarkup(text).replaceAll("\\s+", " ").trim();
    }

    private static String removeMarkup(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ");
    }

    private static String entity(java.util.regex.MatchResult match) {
        try {
            int code = match.group(1) != null ? Integer.parseInt(match.group(1), 16) : Integer.parseInt(match.group(2));
            if (code <= 0 || code > 0x10FFFF) return " ";
            if (Character.isISOControl(code) && code != '\n' && code != '\t') return " ";
            return new String(Character.toChars(code));
        } catch (RuntimeException e) {
            return " ";
        }
    }

    private static String blankToNull(String value, int max) {
        String clipped = clip(value, max);
        return clipped.isBlank() ? null : clipped;
    }

    private static String clip(String value, int max) {
        String clean = value == null ? "" : value.trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
