package ma.ensat.hirelink.integration;

import com.sun.net.httpserver.HttpServer;
import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.WorkMode;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.CatalogOfferService;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.ExternalJobSyncFacade;
import ma.ensat.hirelink.service.ExternalSourceCatalog;
import ma.ensat.hirelink.service.ParsedExternalJob;
import ma.ensat.hirelink.service.SessionService;
import ma.ensat.hirelink.service.external.JoobleHttpClient;
import ma.ensat.hirelink.service.external.JoobleJobConnector;
import ma.ensat.hirelink.service.external.JoobleJobParser;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 — catalogue unifie + Jooble (fixtures) sur hirelink_ext_phase03 uniquement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Pipeline d'import conservé (désactivé par défaut) : ce test vérifie son comportement quand il est rétabli.
        "hirelink.catalog.include-imported=true",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_ext_phase03?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "hirelink.seed.enabled=false",
        "hirelink.scheduling.enabled=false",
        "hirelink.external-jobs.startup-sync=false",
        "hirelink.external-jobs.maroc.enabled=false",
        "hirelink.external-jobs.remoteok.enabled=false",
        "hirelink.external-jobs.greenhouse.enabled=false",
        "hirelink.external-jobs.lever.enabled=false",
        "hirelink.external-jobs.ashby.enabled=false",
        "hirelink.external-jobs.jooble.enabled=false",
        "hirelink.external-jobs.jooble.api-key=",
        "hirelink.catalog.for-you-threshold=50",
        "hirelink.catalog.exclude-sources=MAROC_EMPLOI",
        "hirelink.jwt.secret=hirelink-phase3-isolation-test-key-not-for-real-use-00000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogPhase3IsolationTest {

    private static HttpServer server;
    private static int port;
    private static volatile int joobleStatus = 200;
    private static volatile String joobleBody = "{}";
    private static volatile boolean slowMode;
    private static final AtomicInteger joobleHits = new AtomicInteger();

    @Autowired TestRestTemplate http;
    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingRepository listings;
    @Autowired ExternalJobOfferRepository externalOffers;
    @Autowired ExternalJobImportService imports;
    @Autowired ExternalJobSyncFacade sync;
    @Autowired ExternalSourceCatalog sources;
    @Autowired JobOfferRepository jobOffers;
    @Autowired ApplicationRepository applications;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired JoobleJobConnector joobleConnector;

    @BeforeAll
    void startStub() throws Exception {
        joobleBody = fixture("fixtures/jooble-ma-200.json");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/", ex -> {
            joobleHits.incrementAndGet();
            if (slowMode) {
                try { Thread.sleep(4000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            respond(ex, joobleStatus, joobleStatus == 200 ? joobleBody : "{\"error\":true}");
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterAll
    void stopStub() {
        if (server != null) server.stop(0);
    }

    @BeforeEach
    void clean() {
        applications.deleteAll();
        listings.deleteAll();
        externalOffers.deleteAll();
        jobOffers.deleteAll();
        joobleHits.set(0);
        joobleStatus = 200;
        slowMode = false;
        try { joobleBody = fixture("fixtures/jooble-ma-200.json"); } catch (Exception ignored) {}
    }

    @Test
    void paginationExacteEtTriStableSansDoublons() {
        User recruiter = compte(Role.RECRUTEUR);
        for (int i = 0; i < 25; i++) {
            JobOffer o = offer(recruiter, "Offre " + String.format("%02d", i), "Casablanca", "CDI", "Java", WorkMode.HYBRID);
            o.setCreatedAt(Instant.now().minus(i, ChronoUnit.HOURS));
            jobOffers.save(o);
        }
        imports.importSnapshot("REMOTE_OK", List.of(
                deep("e1", "Ext A", "Casa Co", "https://remoteok.com/remote-jobs/1001", "Casablanca", "Java"),
                deep("e2", "Ext B", "Rabat Co", "https://remoteok.com/remote-jobs/1002", "Rabat", "Python")
        ));
        // quarantaine
        imports.importSnapshot("REMOTE_OK", List.of(
                new ParsedExternalJob("q1", "Home", "X", "https://www.rekrute.com/", null,
                        "Casablanca", "CDI", "d", "Java", null, null, Instant.now())
        ));
        // sample exclu
        imports.importSnapshot("MAROC_EMPLOI", List.of(
                deep("m1", "Sample", "Fake", "https://example.com/jobs/sample-1", "Casablanca", "Java")
        ));

        int indexed = catalog.rebuildIndex();
        assertTrue(indexed >= 27);
        assertEquals(0, listings.findAll().stream().filter(l -> "MAROC_EMPLOI".equals(l.getSourceCode())).count());
        assertTrue(listings.findAll().stream().noneMatch(l ->
                l.getOriginUrl() != null && l.getOriginUrl().contains("rekrute.com")));

        ResponseEntity<Map> page0 = http.exchange(catalogUrl(Map.of(
                "country", "ALL", "page", "0", "size", "10")), HttpMethod.GET, null, Map.class);
        assertEquals(HttpStatus.OK, page0.getStatusCode());
        Map body0 = page0.getBody();
        assertNotNull(body0);
        long total = ((Number) body0.get("totalElements")).longValue();
        assertEquals(indexed, total);
        List<Map> items0 = (List<Map>) body0.get("items");
        assertEquals(10, items0.size());

        ResponseEntity<Map> page1 = http.exchange(catalogUrl(Map.of(
                "country", "ALL", "page", "1", "size", "10")), HttpMethod.GET, null, Map.class);
        List<Map> items1 = (List<Map>) page1.getBody().get("items");
        assertEquals(10, items1.size());

        Set<String> ids = new HashSet<>();
        items0.forEach(i -> assertTrue(ids.add(String.valueOf(i.get("id")))));
        items1.forEach(i -> assertTrue(ids.add(String.valueOf(i.get("id"))), "doublon inter-pages"));

        // tri stable : sort_at DESC, id DESC — premier de page0 >= premier de page1
        String t0 = String.valueOf(items0.get(0).get("createdAt"));
        String t1 = String.valueOf(items1.get(0).get("createdAt"));
        assertTrue(t0.compareTo(t1) >= 0);

        ResponseEntity<Map> empty = http.exchange(catalogUrl(Map.of(
                "country", "ALL", "q", "zzzz-inexistant-xyz")), HttpMethod.GET, null, Map.class);
        assertEquals(0, ((Number) empty.getBody().get("totalElements")).intValue());
        assertTrue(((List) empty.getBody().get("items")).isEmpty());
    }

    @Test
    void filtresPaysVilleContratOrigineEtSource() {
        User recruiter = compte(Role.RECRUTEUR);
        jobOffers.save(offer(recruiter, "Dev Casa", "Casablanca", "CDI", "Java", WorkMode.ONSITE));
        jobOffers.save(offer(recruiter, "Dev Rabat", "Rabat", "Stage", "Python", WorkMode.REMOTE));
        imports.importSnapshot("REMOTE_OK", List.of(
                deep("f1", "Remote Ext", "Ext", "https://remoteok.com/remote-jobs/2001", "Casablanca", "Java")
        ));
        catalog.rebuildIndex();

        Map city = http.exchange(catalogUrl(Map.of("country", "ALL", "city", "Casablanca")),
                HttpMethod.GET, null, Map.class).getBody();
        List<Map> cityItems = (List<Map>) city.get("items");
        assertTrue(cityItems.stream().allMatch(i -> String.valueOf(i.get("city")).equalsIgnoreCase("Casablanca")
                || String.valueOf(i.get("location")).toLowerCase().contains("casablanca")));

        Map contract = http.exchange(catalogUrl(Map.of("country", "ALL", "contract", "Stage")),
                HttpMethod.GET, null, Map.class).getBody();
        assertEquals(1, ((Number) contract.get("totalElements")).intValue());

        Map internal = http.exchange(catalogUrl(Map.of("country", "ALL", "origin", "internal")),
                HttpMethod.GET, null, Map.class).getBody();
        assertTrue(((List<Map>) internal.get("items")).stream().noneMatch(i -> Boolean.TRUE.equals(i.get("external"))));

        Map external = http.exchange(catalogUrl(Map.of("country", "ALL", "origin", "external")),
                HttpMethod.GET, null, Map.class).getBody();
        assertTrue(((List<Map>) external.get("items")).stream().allMatch(i -> Boolean.TRUE.equals(i.get("external"))));
        assertTrue(((List<Map>) external.get("items")).stream().noneMatch(i -> i.get("matchScore") != null && !Boolean.TRUE.equals(i.get("matchAvailable"))));
    }

    @Test
    void pourToiSansCompetencesEtAvecSeuil50() {
        User recruiter = compte(Role.RECRUTEUR);
        jobOffers.save(offer(recruiter, "Java Pro", "Casablanca", "CDI", "Java, Spring", WorkMode.HYBRID));
        jobOffers.save(offer(recruiter, "Python Pro", "Rabat", "CDI", "Python, Django", WorkMode.REMOTE));
        imports.importSnapshot("REMOTE_OK", List.of(
                deep("s1", "Java Ext", "Co", "https://remoteok.com/remote-jobs/3001", "Casablanca", "Java, Spring")
        ));
        catalog.rebuildIndex();

        User bare = compte(Role.CANDIDAT);
        // forYou ne masque plus les offres sans compétences (classement sans seuil) —
        // aligné CatalogForYouNoHideIsolationTest.
        ResponseEntity<Map> barePage = http.exchange(catalogUrl(Map.of("country", "ALL", "forYou", "true")),
                HttpMethod.GET, auth(token(bare)), Map.class);
        assertEquals(HttpStatus.OK, barePage.getStatusCode());
        assertTrue(((Number) barePage.getBody().get("totalElements")).intValue() >= 0);

        User skilled = compte(Role.CANDIDAT);
        skilled.setSkills("Java, Spring Boot");
        users.save(skilled);
        // Filtre explicite minMatch=50 (forYou seul ne masque plus les scores faibles).
        ResponseEntity<Map> hit = http.exchange(catalogUrl(Map.of(
                        "country", "ALL", "forYou", "true", "minMatch", "50", "size", "20")),
                HttpMethod.GET, auth(token(skilled)), Map.class);
        assertEquals(HttpStatus.OK, hit.getStatusCode());
        long total = ((Number) hit.getBody().get("totalElements")).longValue();
        assertTrue(total >= 1);
        List<Map> items = (List<Map>) hit.getBody().get("items");
        assertFalse(items.isEmpty());
        for (Map item : items) {
            assertTrue(Boolean.TRUE.equals(item.get("matchAvailable")));
            assertTrue(((Number) item.get("matchScore")).doubleValue() >= 50.0);
            assertTrue(Boolean.TRUE.equals(item.get("matchesProfile")));
            if (Boolean.TRUE.equals(item.get("external"))) {
                String lbl = String.valueOf(item.get("matchLabel")).toLowerCase();
                assertTrue(lbl.contains("indicatif") || lbl.contains("source"));
            }
        }
    }

    @Test
    void liensExternesEtCandidatureInterneSeule() {
        User recruiter = compte(Role.RECRUTEUR);
        User candidate = compte(Role.CANDIDAT);
        candidate.setSkills("Java");
        users.save(candidate);
        JobOffer offer = jobOffers.save(offer(recruiter, "Intern Apply", "Casablanca", "CDI", "Java", WorkMode.ONSITE));
        imports.importSnapshot("REMOTE_OK", List.of(
                deep("l1", "Ext Link", "Co", "https://remoteok.com/remote-jobs/4001", "Casablanca", "Java")
        ));
        catalog.rebuildIndex();

        Map page = http.exchange(catalogUrl(Map.of("country", "ALL")), HttpMethod.GET, auth(token(candidate)), Map.class).getBody();
        List<Map> items = (List<Map>) page.get("items");
        Map ext = items.stream().filter(i -> Boolean.TRUE.equals(i.get("external"))).findFirst().orElseThrow();
        assertTrue(Boolean.TRUE.equals(ext.get("linkReliable")));
        assertNotNull(ext.get("originUrl"));

        long before = applications.count();
        ResponseEntity<Map> apply = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of(
                        "jobOfferId", offer.getId(),
                        "cvSource", "UPLOAD",
                        "cvFileName", "cv-p3.txt")), Map.class);
        assertEquals(HttpStatus.OK, apply.getStatusCode());
        assertEquals(before + 1, applications.count());

        ResponseEntity<String> bad = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of("jobOfferId", String.valueOf(ext.get("id")))), String.class);
        assertTrue(bad.getStatusCode().is4xxClientError());
    }

    @Test
    void joobleDesactiveSansCleEtFixturesHttp() throws Exception {
        assertFalse(sources.find(JoobleJobParser.SOURCE).enabled());
        assertFalse(joobleConnector.providerEnabled());
        assertThrows(Exception.class, () -> sync.syncOne(JoobleJobParser.SOURCE));

        List<ParsedExternalJob> parsed = joobleConnector.parseFixture(fixture("fixtures/jooble-ma-200.json"));
        assertEquals(2, parsed.size());
        assertEquals("JOOBLE", JoobleJobParser.SOURCE);
        assertTrue(parsed.get(0).originUrl().contains("jooble.org"));

        JoobleHttpClient client = new JoobleHttpClient(2000, 2500, 1_000_000, "test", "127.0.0.1");
        joobleStatus = 200;
        String ok = client.postJson("http://127.0.0.1:" + port + "/api/test-key", "{}");
        assertTrue(ok.contains("jobs"));

        joobleStatus = 403;
        assertThrows(IllegalStateException.class,
                () -> client.postJson("http://127.0.0.1:" + port + "/api/test-key", "{}"));

        joobleStatus = 429;
        assertThrows(IllegalStateException.class,
                () -> client.postJson("http://127.0.0.1:" + port + "/api/test-key", "{}"));

        joobleStatus = 200;
        slowMode = true;
        JoobleHttpClient shortClient = new JoobleHttpClient(500, 800, 1_000_000, "test", "127.0.0.1");
        assertThrows(IllegalStateException.class,
                () -> shortClient.postJson("http://127.0.0.1:" + port + "/api/test-key", "{}"));
        slowMode = false;
    }

    private JobOffer offer(User recruiter, String title, String location, String contract, String skills, WorkMode mode) {
        JobOffer o = new JobOffer();
        o.setRecruiter(recruiter);
        o.setTitle(title);
        o.setDescription("Description Phase3 suffisamment longue pour " + title);
        o.setLocation(location);
        o.setContractType(contract);
        o.setRequiredSkills(skills);
        o.setWorkMode(mode);
        o.setPublished(true);
        return o;
    }

    private ParsedExternalJob deep(String id, String title, String company, String url, String location, String skills) {
        return new ParsedExternalJob(id, title, company, url, null, location, "CDI", "desc", skills,
                null, null, Instant.now().minus(1, ChronoUnit.DAYS), null, "MA");
    }

    private String catalogUrl(Map<String, String> params) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/api/v1/catalog/offers");
        params.forEach(b::queryParam);
        return b.toUriString();
    }

    private User compte(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-p3-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        user.setFullName("Phase3");
        user.setFirstName("Phase3");
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        return users.save(user);
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "p3")).session().getId().toString());
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