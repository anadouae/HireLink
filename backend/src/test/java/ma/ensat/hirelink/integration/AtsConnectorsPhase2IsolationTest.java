package ma.ensat.hirelink.integration;

import com.sun.net.httpserver.HttpServer;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.ExternalJobSyncFacade;
import ma.ensat.hirelink.service.SessionService;
import ma.ensat.hirelink.service.external.AshbyJobParser;
import ma.ensat.hirelink.service.external.ExternalBoardDefinition;
import ma.ensat.hirelink.service.external.AtsHttpClient;
import ma.ensat.hirelink.service.external.GreenhouseJobParser;
import ma.ensat.hirelink.service.external.LeverJobParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 — connecteurs ATS sur schema isole hirelink_ext_phase02 uniquement.
 * HTTP simule via HttpServer local (pas d'Internet requis).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_ext_phase02?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "hirelink.seed.enabled=false",
        "hirelink.scheduling.enabled=false",
        "hirelink.external-jobs.startup-sync=false",
        "hirelink.external-jobs.maroc.enabled=false",
        "hirelink.external-jobs.remoteok.enabled=false",
        "hirelink.external-jobs.greenhouse.enabled=true",
        "hirelink.external-jobs.lever.enabled=true",
        "hirelink.external-jobs.ashby.enabled=true",
        "hirelink.external-jobs.ats.boards-file=classpath:feeds/ats-boards-allowlist-test.v1.json",
        "hirelink.external-jobs.ats.min-interval-ms=0",
        "hirelink.external-jobs.ats.reject-private-ip=false",
        "hirelink.external-jobs.ats.allowed-hosts=127.0.0.1,boards-api.greenhouse.io,api.lever.co,api.ashbyhq.com",
        "hirelink.external-jobs.ats.request-timeout-ms=3000",
        "hirelink.jwt.secret=hirelink-phase2-isolation-test-key-not-for-real-use-00000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AtsConnectorsPhase2IsolationTest {

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger greenhouseHits = new AtomicInteger();
    private static volatile String greenhouseBody;
    private static volatile int greenhouseStatus = 200;
    private static volatile String leverBody;
    private static volatile int leverStatus = 200;
    private static volatile String ashbyBody;
    private static volatile int ashbyStatus = 200;
    private static volatile boolean redirectMode;

    @Autowired TestRestTemplate http;
    @Autowired ExternalJobOfferRepository externalOffers;
    @Autowired ExternalJobImportService imports;
    @Autowired ExternalJobSyncFacade sync;
    @Autowired JobOfferRepository jobOffers;
    @Autowired ApplicationRepository applications;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.core.env.Environment env;

    @BeforeAll
    void startMockAts() throws Exception {
        String url = env.getProperty("spring.datasource.url", "");
        assertTrue(url.contains("hirelink_ext_phase02"),
                "Isolation echouee : datasource doit cibler hirelink_ext_phase02, obtenu=" + url);
        assertFalse(url.contains("/hirelink?"), "Interdit d'utiliser la base principale hirelink");

        greenhouseBody = fixture("fixtures/ats/greenhouse-jobs.json");
        leverBody = fixture("fixtures/ats/lever-postings.json");
        ashbyBody = fixture("fixtures/ats/ashby-jobs.json");

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/v1/boards/acme-gh/jobs", ex -> {
            greenhouseHits.incrementAndGet();
            if (redirectMode) {
                ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/evil");
                ex.sendResponseHeaders(302, -1);
                ex.close();
                return;
            }
            respond(ex, greenhouseStatus, greenhouseBody);
        });
        server.createContext("/v0/postings/acme-lever", ex -> respond(ex, leverStatus, leverBody));
        server.createContext("/posting-api/job-board/acme-ashby", ex -> respond(ex, ashbyStatus, ashbyBody));
        server.createContext("/slow", ex -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            respond(ex, 200, "{}");
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        // Rebind connector bases via system properties is too late; syncBoard uses beans already built.
        // We override by calling import from parser fixtures for functional path, and test HTTP client separately.
        System.setProperty("hirelink.phase2.mock.port", String.valueOf(port));
    }

    @AfterAll
    void stopMock() {
        if (server != null) server.stop(0);
    }

    @BeforeEach
    void clean() {
        externalOffers.deleteAll();
        greenhouseHits.set(0);
        greenhouseStatus = 200;
        leverStatus = 200;
        ashbyStatus = 200;
        redirectMode = false;
        try {
            greenhouseBody = fixture("fixtures/ats/greenhouse-jobs.json");
            leverBody = fixture("fixtures/ats/lever-postings.json");
            ashbyBody = fixture("fixtures/ats/ashby-jobs.json");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void isolationDatasourceConfirmée() {
        String url = env.getProperty("spring.datasource.url", "");
        assertTrue(url.contains("hirelink_ext_phase02"));
        assertFalse(url.contains("hirelink_test"));
        assertFalse(url.matches(".*/hirelink\\?.*"));
    }

    @Test
    void importFixturesAtsQualiteEtApiPublique() throws Exception {
        ExternalBoardDefinition gh = new ExternalBoardDefinition(
                "GREENHOUSE", "acme-gh", "Acme GH", "https://boards.greenhouse.io/acme-gh", "TEST", true, "", "");
        ExternalBoardDefinition lv = new ExternalBoardDefinition(
                "LEVER", "acme-lever", "Acme Lever", "https://jobs.lever.co/acme-lever", "TEST", true, "", "");
        ExternalBoardDefinition as = new ExternalBoardDefinition(
                "ASHBY", "acme-ashby", "Acme Ashby", "https://jobs.ashbyhq.com/acme-ashby", "TEST", true, "", "");

        var ghJobs = new GreenhouseJobParser().parse(fixture("fixtures/ats/greenhouse-jobs.json"), gh);
        var lvJobs = new LeverJobParser().parse(fixture("fixtures/ats/lever-postings.json"), lv);
        var asJobs = new AshbyJobParser().parse(fixture("fixtures/ats/ashby-jobs.json"), as);

        ExternalJobImportService.SyncReport r1 = imports.importSnapshot(GreenhouseJobParser.SOURCE, ghJobs);
        ExternalJobImportService.SyncReport r2 = imports.importSnapshot(LeverJobParser.SOURCE, lvJobs);
        ExternalJobImportService.SyncReport r3 = imports.importSnapshot(AshbyJobParser.SOURCE, asJobs);
        assertTrue(r1.created() >= 1);
        assertTrue(r2.created() >= 1);
        assertTrue(r3.created() >= 1);

        // Homepage Greenhouse mise en quarantaine
        long quarantined = externalOffers.findAll().stream().filter(ExternalJobOffer::isQuarantined).count();
        assertTrue(quarantined >= 1);

        // Sync repetee idempotente
        ExternalJobImportService.SyncReport again = imports.importSnapshot(LeverJobParser.SOURCE, lvJobs);
        assertEquals(0, again.created());
        assertTrue(again.updated() >= 1);

        User candidate = compte(Role.CANDIDAT);
        ResponseEntity<List> list = http.exchange("/api/v1/external-offers", HttpMethod.GET, auth(token(candidate)), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertFalse(list.getBody().isEmpty());
        Map firstDeep = null;
        for (Object o : list.getBody()) {
            Map row = (Map) o;
            if (Boolean.TRUE.equals(row.get("linkReliable"))) {
                firstDeep = row;
                break;
            }
        }
        assertNotNull(firstDeep);
        assertTrue(String.valueOf(firstDeep.get("originUrl")).startsWith("https://"));
        assertNotNull(firstDeep.get("source"));
        assertNotNull(firstDeep.get("boardKey"));
        // Pas de score artificiel sans competences fiables
        assertEquals(Boolean.FALSE, firstDeep.get("matchAvailable"));

        long appsBefore = applications.count();
        ResponseEntity<String> disguised = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of("jobOfferId", firstDeep.get("id"), "cvSource", "UPLOAD", "cvFileName", "x.txt")),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, disguised.getStatusCode());
        assertEquals(appsBefore, applications.count());
    }

    @Test
    void adminHideConserveEtFluxVideConserve() throws Exception {
        ExternalBoardDefinition lv = new ExternalBoardDefinition(
                "LEVER", "acme-lever", "Acme Lever", "https://jobs.lever.co/acme-lever", "TEST", true, "", "");
        var jobs = new LeverJobParser().parse(fixture("fixtures/ats/lever-postings.json"), lv);
        imports.importSnapshot(LeverJobParser.SOURCE, jobs);
        ExternalJobOffer row = externalOffers.findBySource(LeverJobParser.SOURCE).get(0);
        User admin = compte(Role.ADMINISTRATEUR);
        ResponseEntity<String> hide = http.exchange("/api/v1/admin/external-offers/ext-" + row.getId() + "/hide",
                HttpMethod.POST, auth(token(admin)), String.class);
        assertEquals(HttpStatus.OK, hide.getStatusCode());
        imports.importSnapshot(LeverJobParser.SOURCE, jobs);
        ExternalJobOffer still = externalOffers.findById(row.getId()).orElseThrow();
        assertTrue(still.isHiddenByAdmin());
        assertFalse(still.isAvailable());

        ExternalJobImportService.SyncReport empty = imports.importSnapshot(LeverJobParser.SOURCE, List.of());
        assertTrue(empty.keptPrevious());
        assertTrue(externalOffers.count() >= 1);
    }

    @Test
    void nonRegressionCandidatureInterne() {
        User recruiter = compte(Role.RECRUTEUR);
        User candidate = compte(Role.CANDIDAT);
        JobOffer draft = new JobOffer();
        draft.setRecruiter(recruiter);
        draft.setTitle("Internal Java");
        draft.setDescription("Offre interne de non-regression phase 2 assez longue.");
        draft.setRequiredSkills("Java");
        draft.setPublished(true);
        final JobOffer offer = jobOffers.save(draft);
        ResponseEntity<Map> apply = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of("jobOfferId", offer.getId(), "cvSource", "UPLOAD", "cvFileName", "cv.txt")),
                Map.class);
        assertEquals(HttpStatus.OK, apply.getStatusCode());
        assertTrue(applications.findAll().stream().anyMatch(a -> a.getJobOffer().getId().equals(offer.getId())));
    }

    @Test
    void httpClientRefuseRedirectRateLimitEtTimeout() throws Exception {
        AtsHttpClient client = new AtsHttpClient(1000, 1500, 200_000, 0, "test",
                "127.0.0.1", false);
        // 200 OK
        greenhouseStatus = 200;
        String ok = client.getJson("http://127.0.0.1:" + port + "/v1/boards/acme-gh/jobs?content=true");
        assertTrue(ok.contains("Vault Designer"));

        greenhouseStatus = 429;
        assertThrows(IllegalStateException.class,
                () -> client.getJson("http://127.0.0.1:" + port + "/v1/boards/acme-gh/jobs?content=true"));

        greenhouseStatus = 500;
        assertThrows(IllegalStateException.class,
                () -> client.getJson("http://127.0.0.1:" + port + "/v1/boards/acme-gh/jobs?content=true"));

        greenhouseStatus = 404;
        assertThrows(IllegalStateException.class,
                () -> client.getJson("http://127.0.0.1:" + port + "/v1/boards/acme-gh/jobs?content=true"));

        redirectMode = true;
        greenhouseStatus = 200;
        assertThrows(IllegalStateException.class,
                () -> client.getJson("http://127.0.0.1:" + port + "/v1/boards/acme-gh/jobs?content=true"));
        redirectMode = false;

        assertThrows(IllegalStateException.class,
                () -> client.getJson("http://127.0.0.1:" + port + "/slow"));
    }

    private User compte(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-p2-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        user.setFullName("Phase2");
        user.setFirstName("Phase2");
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        return users.save(user);
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "p2")).session().getId().toString());
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Object> json(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String fixture(String path) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}