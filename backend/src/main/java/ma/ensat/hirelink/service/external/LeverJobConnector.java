package ma.ensat.hirelink.service.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LeverJobConnector extends AbstractAtsJobConnector {

    private final String apiBase;

    public LeverJobConnector(
            ExternalBoardRegistry registry,
            AtsHttpClient http,
            @Value("${hirelink.external-jobs.lever.enabled:false}") boolean enabled,
            @Value("${hirelink.external-jobs.lever.api-base:https://api.lever.co}") String apiBase) {
        super(registry, http, enabled, (body, board) -> new LeverJobParser().parse(body, board));
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
    }

    @Override
    public String sourceCode() {
        return LeverJobParser.SOURCE;
    }

    @Override
    public String label() {
        return "Lever Postings";
    }

    @Override
    public String homeUrl() {
        return "https://www.lever.co";
    }

    @Override
    protected String buildUrl(ExternalBoardDefinition board) {
        return apiBase + "/v0/postings/" + encode(board.boardKey()) + "?mode=json&limit=100";
    }

    private static String encode(String key) {
        return java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
    }
}