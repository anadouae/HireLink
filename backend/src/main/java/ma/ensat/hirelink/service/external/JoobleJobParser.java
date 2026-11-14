package ma.ensat.hirelink.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.service.ParsedExternalJob;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parse la reponse JSON Jooble (MA / regionale).
 * Pas de score invente ; publishedAt uniquement si "updated" Jooble est parseable.
 */
public class JoobleJobParser {

    public static final String SOURCE = "JOOBLE";

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ParsedExternalJob> parse(String body) {
        try {
            JsonNode root = mapper.readTree(body == null ? "{}" : body);
            JsonNode jobs = root.path("jobs");
            if (!jobs.isArray()) {
                throw new IllegalArgumentException("Reponse Jooble sans tableau jobs");
            }
            List<ParsedExternalJob> out = new ArrayList<>();
            for (JsonNode n : jobs) {
                ParsedExternalJob job = mapOne(n);
                if (job != null) out.add(job);
            }
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON Jooble illisible : " + e.getMessage(), e);
        }
    }

    private ParsedExternalJob mapOne(JsonNode n) {
        String id = text(n, "id");
        String title = text(n, "title");
        String link = text(n, "link");
        if (title == null || link == null) return null;
        if (id == null || id.isBlank()) id = Integer.toHexString(link.hashCode());
        String company = text(n, "company");
        if (company == null || company.isBlank()) company = "Entreprise (via Jooble)";
        String location = text(n, "location");
        String type = text(n, "type");
        String snippet = text(n, "snippet");
        Instant updated = parseUpdated(text(n, "updated"));
        return new ParsedExternalJob(
                id,
                title,
                company,
                link,
                link,
                location,
                type,
                snippet,
                null,
                null,
                null,
                updated,
                null,
                guessCountry(location)
        );
    }

    private static String guessCountry(String location) {
        if (location == null) return "MA";
        String fold = location.toLowerCase(Locale.ROOT);
        if (fold.contains("maroc") || fold.contains("morocco") || fold.contains("casablanca")
                || fold.contains("rabat") || fold.contains("tanger") || fold.contains("marrakech")
                || fold.contains("fes") || fold.contains("agadir")
                || fold.contains("tetouan")) {
            return "MA";
        }
        return "MA";
    }

    private static Instant parseUpdated(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignored) { /* fallthrough */ }
        try {
            LocalDate d = LocalDate.parse(t, DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT));
            return d.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) { /* fallthrough */ }
        try {
            LocalDate d = LocalDate.parse(t.substring(0, Math.min(10, t.length())));
            return d.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }
}