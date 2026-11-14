package ma.ensat.hirelink.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Allowlist des boards ATS. Chargee depuis classpath JSON.
 * enabled=false par defaut dans le fichier de seed : aucun appel reseau implicite.
 */
@Component
public class ExternalBoardRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExternalBoardRegistry.class);

    private final List<ExternalBoardDefinition> boards;
    private final ObjectMapper json = new ObjectMapper();

    public ExternalBoardRegistry(
            ResourceLoader resources,
            @Value("${hirelink.external-jobs.ats.boards-file:classpath:feeds/ats-boards-allowlist.v1.json}") String location) {
        this.boards = List.copyOf(load(resources, location));
        log.info("Allowlist ATS chargee : {} board(s), {} actif(s)",
                boards.size(), boards.stream().filter(ExternalBoardDefinition::enabled).count());
    }

    public List<ExternalBoardDefinition> all() {
        return boards;
    }

    public List<ExternalBoardDefinition> enabledFor(String provider) {
        String key = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        return boards.stream()
                .filter(b -> key.equals(b.sourceCode()))
                .filter(ExternalBoardDefinition::enabled)
                .toList();
    }

    public Optional<ExternalBoardDefinition> find(String provider, String boardKey) {
        String p = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        String k = boardKey == null ? "" : boardKey.trim();
        return boards.stream()
                .filter(b -> p.equals(b.sourceCode()) && k.equalsIgnoreCase(b.boardKey()))
                .findFirst();
    }

    private List<ExternalBoardDefinition> load(ResourceLoader resources, String location) {
        try {
            Resource resource = resources.getResource(location);
            if (!resource.exists()) {
                log.warn("Fichier allowlist ATS introuvable : {}", location);
                return List.of();
            }
            String body;
            try (InputStream in = resource.getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonNode root = json.readTree(body);
            JsonNode arr = root.get("boards");
            if (arr == null || !arr.isArray()) return List.of();
            List<ExternalBoardDefinition> out = new ArrayList<>();
            for (JsonNode n : arr) {
                if (n == null || !n.isObject()) continue;
                String provider = text(n, "provider");
                String boardKey = text(n, "boardKey");
                String company = text(n, "companyName");
                if (provider.isBlank() || boardKey.isBlank() || company.isBlank()) continue;
                out.add(new ExternalBoardDefinition(
                        provider.toUpperCase(Locale.ROOT),
                        boardKey,
                        company,
                        text(n, "referenceUrl"),
                        text(n, "regionHint"),
                        n.path("enabled").asBoolean(false),
                        text(n, "attributionNotes"),
                        text(n, "verificationNotes")
                ));
            }
            return out;
        } catch (Exception e) {
            log.warn("Allowlist ATS illisible : {}", e.toString());
            return List.of();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }
}