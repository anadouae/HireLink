package ma.ensat.hirelink.service.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AshbyJobConnector extends AbstractAtsJobConnector {

    private final String apiBase;

    public AshbyJobConnector(
            ExternalBoardRegistry registry,
            AtsHttpClient http,
            @Value("${hirelink.external-jobs.ashby.enabled:false}") boolean enabled,
            @Value("${hirelink.external-jobs.ashby.api-base:https://api.ashbyhq.com}") String apiBase) {
        super(registry, http, enabled, (body, board) -> new AshbyJobParser().parse(body, board));
        this.apiBase = trimSlash(apiBase);
    }

    @Override
    public String sourceCode() { return AshbyJobParser.SOURCE; }

    @Override
    public String homeUrl() { return "https://www.ashbyhq.com"; }

    @Override
    public String label() { return "Ashby Job Board"; }

    @Override
    protected String buildUrl(ExternalBoardDefinition board) {
        return apiBase + "/posting-api/job-board/" + encode(board.boardKey());
    }

    private static String encode(String key) {
        return java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String trimSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}