package ma.ensat.hirelink.service.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GreenhouseJobConnector extends AbstractAtsJobConnector {

    private final String apiBase;
    private final boolean includeContent;

    public GreenhouseJobConnector(
            ExternalBoardRegistry registry,
            AtsHttpClient http,
            @Value("${hirelink.external-jobs.greenhouse.enabled:false}") boolean enabled,
            @Value("${hirelink.external-jobs.greenhouse.api-base:https://boards-api.greenhouse.io}") String apiBase,
            @Value("${hirelink.external-jobs.greenhouse.include-content:false}") boolean includeContent) {
        super(registry, http, enabled, (body, board) -> new GreenhouseJobParser().parse(body, board));
        this.apiBase = trimSlash(apiBase);
        this.includeContent = includeContent;
    }

    @Override
    public String sourceCode() { return GreenhouseJobParser.SOURCE; }

    @Override
    public String label() { return "Greenhouse Job Board"; }

    @Override
    public String homeUrl() { return "https://www.greenhouse.io"; }

    @Override
    protected String buildUrl(ExternalBoardDefinition board) {
        // Sans content=true : titre, lieu, absolute_url (léger). Avec content : descriptions HTML (très volumineux).
        return apiBase + "/v1/boards/" + encode(board.boardKey()) + "/jobs"
                + (includeContent ? "?content=true" : "");
    }

    private static String encode(String key) {
        return java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String trimSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}