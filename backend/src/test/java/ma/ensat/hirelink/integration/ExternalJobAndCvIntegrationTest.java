package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.Application;
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
import ma.ensat.hirelink.service.RemoteOkParser;
import ma.ensat.hirelink.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExternalJobAndCvIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired JobOfferRepository jobOffers;
    @Autowired ApplicationRepository applications;
    @Autowired ExternalJobOfferRepository externalOffers;
    @Autowired ExternalJobImportService imports;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void isoleLesAnnoncesExternes() {
        externalOffers.deleteAll();
    }

    @Test
    void laSynchroIsoleeDedupliqueEtRetireSansAppelReseau() {
        RemoteOkParser parser = new RemoteOkParser();
        String first = """
                [{"legal":"link back"},
                 {"id":1,"position":"Java Developer","company":"Northwind","url":"https://remoteok.com/remote-jobs/1","tags":["java"]},
                 {"id":1,"position":"Java Developer","company":"Northwind","url":"https://remoteok.com/remote-jobs/1","tags":["java"]},
                 {"id":2,"position":"Designer","company":"Acme","url":"javascript:alert(1)"}]
                """;
        ExternalJobImportService.SyncReport created = imports.importSnapshot(RemoteOkParser.SOURCE, parser.parse(first));
        assertEquals(1, created.created());
        assertEquals(1, created.duplicates());
        assertEquals(1, externalOffers.findByAvailableTrueOrderByPublishedAtDesc().size());

        ExternalJobImportService.SyncReport empty = imports.importSnapshot(RemoteOkParser.SOURCE, List.of());
        assertTrue(empty.keptPrevious());
        assertEquals(1, externalOffers.findByAvailableTrueOrderByPublishedAtDesc().size());

        String next = """
                [{"id":3,"position":"Support","company":"Globex","url":"https://remoteok.com/remote-jobs/3"}]
                """;
        ExternalJobImportService.SyncReport replaced = imports.importSnapshot(RemoteOkParser.SOURCE, parser.parse(next));
        assertEquals(1, replaced.created());
        assertEquals(0, replaced.withdrawn());
        assertEquals(2, externalOffers.findByAvailableTrueOrderByPublishedAtDesc().size());

        externalOffers.findBySource(RemoteOkParser.SOURCE).stream()
                .filter(row -> "Northwind".equals(row.getCompany()))
                .forEach(row -> {
                    row.setLastSeenAt(java.time.Instant.now().minus(java.time.Duration.ofDays(8)));
                    externalOffers.save(row);
                });
        ExternalJobImportService.SyncReport aged = imports.importSnapshot(RemoteOkParser.SOURCE, parser.parse(next));
        assertEquals(1, aged.withdrawn());
        assertEquals(1, externalOffers.findByAvailableTrueOrderByPublishedAtDesc().size());
        assertEquals("Globex", externalOffers.findByAvailableTrueOrderByPublishedAtDesc().get(0).getCompany());
    }

    @Test
    void leFluxDesactiveNePartPasEtUneOffreExterneNouvrePasDeCandidature() {
        User admin = compte(Role.ADMINISTRATEUR);
        User candidate = compte(Role.CANDIDAT);
        candidate.setSkills("Java, SQL");
        users.save(candidate);
        ResponseEntity<String> anonymous = http.postForEntity("/api/v1/external-offers/sync", null, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
        ResponseEntity<String> denied = http.exchange("/api/v1/external-offers/sync", HttpMethod.POST, auth(token(candidate)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        ResponseEntity<String> disabled = http.exchange("/api/v1/external-offers/sync", HttpMethod.POST, auth(token(admin)), String.class);
        assertEquals(HttpStatus.CONFLICT, disabled.getStatusCode());
        assertTrue(disabled.getBody().contains("désactivé"));

        imports.importSnapshot(RemoteOkParser.SOURCE, new RemoteOkParser().parse("""
                [{"id":9,"position":"Java Developer","company":"Northwind","url":"https://remoteok.com/remote-jobs/9","location":"Worldwide","tags":["java"]},
                 {"id":10,"position":"Writer","company":"Bare","url":"https://remoteok.com/remote-jobs/10"}]
                """));
        ResponseEntity<List> list = http.exchange("/api/v1/external-offers", HttpMethod.GET, auth(token(candidate)), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        Map offer = null;
        for (Object item : list.getBody()) {
            Map row = (Map) item;
            if ("java".equals(row.get("requiredSkills"))) offer = row;
        }
        assertTrue(offer != null);
        assertEquals(Boolean.TRUE, offer.get("external"));
        assertEquals(Boolean.FALSE, offer.get("companyVerified"));
        assertTrue(String.valueOf(offer.get("id")).startsWith("ext-"));
        assertEquals(Boolean.TRUE, offer.get("matchAvailable"));
        assertTrue(String.valueOf(offer.get("matchLabel")).contains("Indicatif"));
        assertTrue(offer.get("applyUrl").toString().startsWith("https://"));
        Map bare = null;
        for (Object item : list.getBody()) {
            Map row = (Map) item;
            if ("Bare".equals(row.get("companyName"))) bare = row;
        }
        assertTrue(bare != null);
        assertEquals(Boolean.FALSE, bare.get("matchAvailable"));
        assertEquals("Correspondance non disponible", bare.get("matchLabel"));

        long before = applications.count();
        ResponseEntity<String> apply = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of("jobOfferId", 999999999L)), String.class);
        assertEquals(HttpStatus.NOT_FOUND, apply.getStatusCode());
        ResponseEntity<String> disguised = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of("jobOfferId", offer.get("id"))), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, disguised.getStatusCode());
        assertEquals(before, applications.count());

        ResponseEntity<String> hidden = http.exchange("/api/v1/admin/external-offers/" + offer.get("id") + "/hide",
                HttpMethod.POST, auth(token(admin)), String.class);
        assertEquals(HttpStatus.OK, hidden.getStatusCode());
        ResponseEntity<List> afterHide = http.exchange("/api/v1/external-offers", HttpMethod.GET, auth(token(candidate)), List.class);
        assertTrue(afterHide.getBody().stream().noneMatch(item -> "Northwind".equals(((Map) item).get("companyName"))
                && "java".equals(((Map) item).get("requiredSkills"))));
        imports.importSnapshot(RemoteOkParser.SOURCE, new RemoteOkParser().parse("""
                [{"id":9,"position":"Java Developer","company":"Northwind","url":"https://remoteok.com/remote-jobs/9","location":"Worldwide","tags":["java"]}]
                """));
        ResponseEntity<List> stillHidden = http.exchange("/api/v1/external-offers", HttpMethod.GET, auth(token(candidate)), List.class);
        assertTrue(stillHidden.getBody().stream().noneMatch(item -> "java".equals(((Map) item).get("requiredSkills"))));

        ResponseEntity<Map> status = http.exchange("/api/v1/admin/external-offers/status", HttpMethod.GET, auth(token(admin)), Map.class);
        assertEquals(HttpStatus.OK, status.getStatusCode());
        assertEquals(Boolean.FALSE, status.getBody().get("enabled"));
        ResponseEntity<String> statusDenied = http.exchange("/api/v1/admin/external-offers/status", HttpMethod.GET, auth(token(candidate)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, statusDenied.getStatusCode());
    }

    @Test
    void confirmerUnCvNeChangePasLeScoreDepose() {
        User recruiter = compte(Role.RECRUTEUR);
        User candidate = compte(Role.CANDIDAT);
        JobOffer offer = new JobOffer();
        offer.setRecruiter(recruiter);
        offer.setTitle("Offre interne");
        offer.setDescription("Description de recette assez longue pour le dossier.");
        offer = jobOffers.save(offer);
        Application application = new Application();
        application.setCandidate(candidate);
        application.setJobOffer(offer);
        application.setMatchScore(42.0);
        application = applications.save(application);

        String sections = "{\"identity\":\"Sara\",\"experience\":\"STAGE · developpeur Java · Atlas Digital\",\"skills\":\"Java\",\"languages\":\"Francais\",\"experiences\":[{\"title\":\"developpeur Java\",\"organization\":\"Atlas Digital\",\"kind\":\"STAGE\"}]}";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(candidate));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("Sara\nEXPERIENCES\nStage developpeur Java, Atlas Digital\n".getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "cv.txt"; }
        });
        body.add("sections", sections);
        ResponseEntity<String> saved = http.exchange("/api/v1/me/cv", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertEquals(42.0, applications.findById(application.getId()).orElseThrow().getMatchScore());
    }

    @Test
    void deuxSynchronisationsSimultaneesNeCreentPasDeDoublon() throws Exception {
        ExternalJobImportService target = AopTestUtils.getTargetObject(imports);
        var field = ExternalJobImportService.class.getDeclaredField("syncLock");
        field.setAccessible(true);
        ReentrantLock lock = (ReentrantLock) field.get(target);
        assertTrue(lock.tryLock());
        ExternalJobImportService.SyncReport refused;
        try {
            ExecutorService one = Executors.newSingleThreadExecutor();
            Future<ExternalJobImportService.SyncReport> pending = one.submit(() ->
                    imports.importSnapshot(RemoteOkParser.SOURCE, List.of()));
            refused = pending.get(10, TimeUnit.SECONDS);
            one.shutdownNow();
        } finally {
            lock.unlock();
        }
        assertEquals(0, refused.created());
        assertEquals(0, refused.withdrawn());
        assertTrue(refused.keptPrevious());
        assertTrue(refused.message().contains("deja en cours"));

        List<RemoteOkParser.ParsedJob> jobs = new RemoteOkParser().parse("""
                [{"id":"race-1","position":"Java","company":"Acme","url":"https://remoteok.com/remote-jobs/race-1"}]
                """);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ExternalJobImportService.SyncReport> first = pool.submit(() -> {
            start.await();
            return imports.importSnapshot(RemoteOkParser.SOURCE, jobs);
        });
        Future<ExternalJobImportService.SyncReport> second = pool.submit(() -> {
            start.await();
            return imports.importSnapshot(RemoteOkParser.SOURCE, jobs);
        });
        start.countDown();
        ExternalJobImportService.SyncReport left = first.get(20, TimeUnit.SECONDS);
        ExternalJobImportService.SyncReport right = second.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        long rows = externalOffers.findBySource(RemoteOkParser.SOURCE).stream()
                .filter(row -> "race-1".equals(row.getExternalId())).count();
        assertEquals(1, rows);
        assertEquals(1, left.created() + right.created());
        assertEquals(0, left.withdrawn() + right.withdrawn());
    }

    private User compte(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        user.setFullName("Compte Recette");
        user.setFirstName("Compte");
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        return users.save(user);
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
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
}
