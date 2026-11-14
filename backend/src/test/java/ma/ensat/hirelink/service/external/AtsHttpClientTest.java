package ma.ensat.hirelink.service.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtsHttpClientTest {

    private final AtsHttpClient client = new AtsHttpClient(
            2000, 3000, 100_000, 0, "HireLink-Test",
            "boards-api.greenhouse.io,api.lever.co,api.ashbyhq.com,127.0.0.1",
            false);

    @Test
    void refuseHoteNonAllowlisteEtUserinfo() {
        assertThrows(IllegalArgumentException.class,
                () -> client.validateUrl("https://evil.example/jobs"));
        assertThrows(IllegalArgumentException.class,
                () -> client.validateUrl("https://user:pass@boards-api.greenhouse.io/v1/boards/x/jobs"));
        assertThrows(IllegalArgumentException.class,
                () -> client.validateUrl("javascript:alert(1)"));
    }

    @Test
    void accepteHotesAtsDocumentes() {
        assertNotNull(client.validateUrl("https://boards-api.greenhouse.io/v1/boards/gitlab/jobs"));
        assertNotNull(client.validateUrl("https://api.lever.co/v0/postings/leverdemo?mode=json"));
        assertNotNull(client.validateUrl("https://api.ashbyhq.com/posting-api/job-board/Ashby"));
    }
}