package ma.ensat.hirelink.service.external;

import ma.ensat.hirelink.service.ParsedExternalJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Squelette commun : sync multi-boards avec isolation des erreurs par board. */
public abstract class AbstractAtsJobConnector implements ExternalJobConnector {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ExternalBoardRegistry registry;
    private final AtsHttpClient http;
    private final boolean enabled;
    private final BiFunction<String, ExternalBoardDefinition, List<ParsedExternalJob>> parse;

    protected AbstractAtsJobConnector(
            ExternalBoardRegistry registry,
            AtsHttpClient http,
            boolean enabled,
            BiFunction<String, ExternalBoardDefinition, List<ParsedExternalJob>> parse) {
        this.registry = registry;
        this.http = http;
        this.enabled = enabled;
        this.parse = parse;
    }

    @Override
    public boolean providerEnabled() {
        return enabled;
    }

    @Override
    public List<ParsedExternalJob> fetchNormalized() {
        if (!enabled) {
            throw new IllegalStateException("Provider " + sourceCode() + " desactive");
        }
        List<ExternalBoardDefinition> boards = registry.enabledFor(sourceCode());
        if (boards.isEmpty()) {
            throw new IllegalStateException("Aucun board allowliste actif pour " + sourceCode());
        }
        List<ParsedExternalJob> all = new ArrayList<>();
        int failures = 0;
        IllegalStateException last = null;
        for (ExternalBoardDefinition board : boards) {
            try {
                all.addAll(fetchOne(board));
            } catch (IllegalStateException | IllegalArgumentException e) {
                failures++;
                last = e instanceof IllegalStateException ise ? ise : new IllegalStateException(e.getMessage());
                log.warn("Board {}/{} ignore : {}", sourceCode(), board.boardKey(), e.toString());
            }
        }
        if (all.isEmpty() && failures > 0 && last != null) {
            throw last;
        }
        return all;
    }

    @Override
    public List<ParsedExternalJob> fetchNormalized(String boardKey) {
        if (!enabled) {
            throw new IllegalStateException("Provider " + sourceCode() + " desactive");
        }
        ExternalBoardDefinition board = registry.find(sourceCode(), boardKey)
                .orElseThrow(() -> new IllegalArgumentException("Board inconnu : " + boardKey));
        if (!board.enabled()) {
            throw new IllegalStateException("Board " + boardKey + " desactive dans l'allowlist");
        }
        return fetchOne(board);
    }

    private List<ParsedExternalJob> fetchOne(ExternalBoardDefinition board) {
        String url = buildUrl(board);
        String body = http.getJson(url);
        return parse.apply(body, board);
    }

    protected abstract String buildUrl(ExternalBoardDefinition board);
}