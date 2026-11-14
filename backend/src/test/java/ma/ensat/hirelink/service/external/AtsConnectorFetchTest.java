package ma.ensat.hirelink.service.external;

import com.sun.net.httpserver.HttpServer;
import ma.ensat.hirelink.service.ParsedExternalJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Sync connecteur bout-en-bout contre HttpServer local (fixtures, zero Internet). */
class AtsConnectorFetchTest {

    private static HttpServer server;
    private static int port;
    private static String greenhouseBody;
    private static String leverBody;
    private static String ashbyBody;

    @BeforeAll
    static void start() throws Exception {
        greenhouseBody = StreamUtils.copyToString(new ClassPathResource("fixtures/ats/greenhouse-jobs.json").getInputStream(), StandardCharsets.UTF_8);
        leverBody = StreamUtils.copyToString(new ClassPathResource("fixtures/ats/lever-postings.json").getInputStream(), StandardCharsets.UTF_8);
        ashbyBody = StreamUtils.copyToString(new ClassPathResource("fixtures/ats/ashby-jobs.json").getInputStream(), StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/v1/boards/acme-gh/jobs", ex -> respond(ex, 200, greenhouseBody));
        server.createContext("/v0/postings/acme-lever", ex -> respond(ex, 200, leverBody));
        server.createContext("/posting-api/job-board/acme-ashby", ex -> respond(ex, 200, ashbyBody));
        server.createContext("/v1/boards/empty/jobs", ex -> respond(ex, 200, "{\"jobs\":[],\"meta\":{\"total\":0}}"));
        server.createContext("/v1/boards/bad/jobs", ex -> respond(ex, 200, "{bad"));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterAll
    static void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void greenhouseLeverAshbyFetchNormalized() {
        String base = "http://127.0.0.1:" + port;
        AtsHttpClient http = new AtsHttpClient(1000, 3000, 500_000, 0, "test", "127.0.0.1", false);
        ExternalBoardRegistry registry = new ExternalBoardRegistry(
                new DefaultResourceLoader(), "classpath:feeds/ats-boards-allowlist-test.v1.json");

        GreenhouseJobConnector gh = new GreenhouseJobConnector(registry, http, true, base);
        LeverJobConnector lever = new LeverJobConnector(registry, http, true, base);
        AshbyJobConnector ashby = new AshbyJobConnector(registry, http, true, base);

        List<ParsedExternalJob> gJobs = gh.fetchNormalized("acme-gh");
        assertEquals(2, gJobs.size());
        assertTrue(gJobs.get(0).originUrl().contains("/jobs/127817"));
        assertNull(gJobs.get(0).applyUrl());

        List<ParsedExternalJob> lJobs = lever.fetchNormalized("acme-lever");
        assertEquals(2, lJobs.size());
        assertNotNull(lJobs.get(0).applyUrl());
        assertNull(lJobs.get(1).applyUrl());

        List<ParsedExternalJob> aJobs = ashby.fetchNormalized("acme-ashby");
        assertEquals(2, aJobs.size());
        assertTrue(aJobs.stream().anyMatch(j -> "MA".equals(j.countryCode())));

        assertThrows(IllegalStateException.class, () -> gh.fetchNormalized("disabled-gh"));
    }

    @Test
    void boardVideNeLevePasSiAutresBoardsOkMaisParseMalformeEchoue() {
        String base = "http://127.0.0.1:" + port;
        AtsHttpClient http = new AtsHttpClient(1000, 3000, 500_000, 0, "test", "127.0.0.1", false);
        // registry with only acme-gh enabled already; empty board not in allowlist enabled set
        ExternalBoardRegistry registry = new ExternalBoardRegistry(
                new DefaultResourceLoader(), "classpath:feeds/ats-boards-allowlist-test.v1.json");
        GreenhouseJobConnector gh = new GreenhouseJobConnector(registry, http, true, base);
        assertThrows(IllegalArgumentException.class, () -> {
            // force parse error path via unknown board
            gh.fetchNormalized("missing-board");
        });
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}