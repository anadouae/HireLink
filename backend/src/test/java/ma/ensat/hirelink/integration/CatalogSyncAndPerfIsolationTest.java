package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.WorkMode;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.CatalogListingIndexer;
import ma.ensat.hirelink.service.CatalogOfferService;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.JobOfferService;
import ma.ensat.hirelink.service.ParsedExternalJob;
import ma.ensat.hirelink.service.SessionService;
import ma.ensat.hirelink.dto.ApiDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recette pre-deploiement : sync incrementale catalogue + perf + Pour toi sans HTTP 400.
 * Schema isole hirelink_ext_phase03 uniquement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
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
        "hirelink.catalog.for-you-threshold=50",
        "hirelink.catalog.for-you-max-scan=200",
        "hirelink.catalog.exclude-sources=MAROC_EMPLOI",
        "hirelink.jwt.secret=hirelink-phase3-audit-test-key-not-for-real-use-000000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogSyncAndPerfIsolationTest {

    @Autowired TestRestTemplate http;
    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingIndexer indexer;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired JobOfferService jobOfferService;
    @Autowired ExternalJobOfferRepository externalOffers;
    @Autowired ExternalJobImportService imports;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clean() {
        listings.deleteAll();
        externalOffers.deleteAll();
        jobOffers.deleteAll();
    }

    @Test
    void syncIncrementalPublishUpdateCloseAndExternalQuarantine() {
        // ADMINISTRATEUR : canManageOffer sans organisation (recruiter nu echoue a publish).
        User admin = compte(Role.ADMINISTRATEUR);

        ApiDtos.JobOfferRequest req = newOfferReq("Dev Sync Casa", "Casablanca", "CDI", "Java, Spring", false);
        var created = jobOfferService.create(admin, req);
        assertEquals(0, listings.count());

        jobOfferService.publish(admin, created.id());
        assertEquals(1, listings.count());
        assertTrue(listings.findAll().stream().anyMatch(l -> "Dev Sync Casa".equals(l.getTitle())));

        ApiDtos.JobOfferRequest upd = newOfferReq("Dev Sync Rabat", "Rabat", "CDI", "Java, Spring", true);
        jobOfferService.update(admin, created.id(), upd);
        assertEquals(1, listings.count());
        assertEquals("Dev Sync Rabat", listings.findAll().get(0).getTitle());
        assertTrue(listings.findAll().get(0).getLocationLabel().toLowerCase().contains("rabat")
                || "Rabat".equalsIgnoreCase(listings.findAll().get(0).getCity()));

        jobOfferService.close(admin, created.id());
        assertEquals(0, listings.count());

        imports.importSnapshot("REMOTE_OK", List.of(
                new ParsedExternalJob("deep-s1", "Ext Deep", "Co",
                        "https://remoteok.com/remote-jobs/9001", null,
                        "Casablanca", "CDI", "d", "Java", null, null, Instant.now()),
                new ParsedExternalJob("home-s1", "Ext Home", "Co",
                        "https://www.rekrute.com/", null,
                        "Casablanca", "CDI", "d", "Java", null, null, Instant.now())
        ));
        assertEquals(1, listings.count());
        assertTrue(listings.findAll().stream().noneMatch(l -> l.getOriginUrl() != null && l.getOriginUrl().contains("rekrute")));
    }

    @Test
    void pourToiTruncatesWithoutHttp400AndUsesMulticriteriaInternal() {
        User recruiter = compte(Role.RECRUTEUR);
        for (int i = 0; i < 250; i++) {
            JobOffer o = new JobOffer();
            o.setRecruiter(recruiter);
            o.setTitle("Bulk " + i);
            o.setDescription("Description assez longue pour offre bulk " + i);
            o.setLocation(i % 2 == 0 ? "Casablanca" : "Rabat");
            o.setContractType("CDI");
            o.setRequiredSkills("Java, Spring");
            o.setWorkMode(WorkMode.HYBRID);
            o.setPublished(true);
            o.setCreatedAt(Instant.now().minus(i, ChronoUnit.MINUTES));
            jobOffers.save(o);
        }
        catalog.rebuildIndex();
        assertTrue(listings.count() >= 250);

        User candidate = compte(Role.CANDIDAT);
        candidate.setSkills("Java, Spring Boot");
        candidate.setPreferredContracts("CDI");
        candidate.setPreferredLocations("Casablanca");
        users.save(candidate);

        ResponseEntity<Map> res = http.exchange(
                UriComponentsBuilder.fromPath("/api/v1/catalog/offers")
                        .queryParam("country", "ALL")
                        .queryParam("forYou", "true")
                        .queryParam("size", "20")
                        .toUriString(),
                HttpMethod.GET, auth(token(candidate)), Map.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(Boolean.TRUE, res.getBody().get("forYouTruncated"));
        assertTrue(((Number) res.getBody().get("forYouScanned")).intValue() <= 200);
        List<Map> items = (List<Map>) res.getBody().get("items");
        assertFalse(items.isEmpty());
        Map first = items.get(0);
        assertTrue(Boolean.TRUE.equals(first.get("matchAvailable")));
        String label = String.valueOf(first.get("matchLabel")).toLowerCase()
                .replace('é', 'e').replace('è', 'e').replace('ê', 'e');
        assertTrue(label.contains("multicritere")
                || label.contains("compatibilite")
                || label.contains("competences"),
                () -> "matchLabel inattendu: " + first.get("matchLabel"));
        assertTrue(((Number) first.get("matchScore")).doubleValue() >= 50.0);
    }

    @Test
    void performanceSeedAndMeasure() {
        User recruiter = compte(Role.RECRUTEUR);
        int n = 1000;
        List<JobOffer> batch = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            JobOffer o = new JobOffer();
            o.setRecruiter(recruiter);
            o.setTitle("Perf " + i);
            o.setDescription("Description performance " + i);
            o.setLocation(switch (i % 5) {
                case 0 -> "Casablanca";
                case 1 -> "Rabat";
                case 2 -> "Tanger";
                case 3 -> "Marrakech";
                default -> "Tetouan";
            });
            o.setContractType(i % 2 == 0 ? "CDI" : "Stage");
            o.setRequiredSkills(i % 3 == 0 ? "Java, Spring" : "Python, SQL");
            o.setWorkMode(WorkMode.HYBRID);
            o.setPublished(true);
            o.setCreatedAt(Instant.now().minus(i, ChronoUnit.MINUTES));
            batch.add(o);
        }
        jobOffers.saveAll(batch);

        long t0 = System.nanoTime();
        int indexed = catalog.rebuildIndex();
        long rebuildMs = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals(n, indexed);

        t0 = System.nanoTime();
        ResponseEntity<Map> page = http.exchange(
                UriComponentsBuilder.fromPath("/api/v1/catalog/offers")
                        .queryParam("country", "ALL").queryParam("page", "0").queryParam("size", "20")
                        .toUriString(),
                HttpMethod.GET, null, Map.class);
        long pageMs = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertEquals(n, ((Number) page.getBody().get("totalElements")).intValue());

        t0 = System.nanoTime();
        ResponseEntity<Map> filtered = http.exchange(
                UriComponentsBuilder.fromPath("/api/v1/catalog/offers")
                        .queryParam("country", "ALL").queryParam("city", "Casablanca").queryParam("contract", "CDI")
                        .toUriString(),
                HttpMethod.GET, null, Map.class);
        long filterMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(((Number) filtered.getBody().get("totalElements")).intValue() > 0);

        // accent / fold city : requete sans accent doit matcher la ville canonique indexee
        ResponseEntity<Map> tetouan = http.exchange(
                UriComponentsBuilder.fromPath("/api/v1/catalog/offers")
                        .queryParam("country", "ALL").queryParam("city", "Tetouan")
                        .toUriString(),
                HttpMethod.GET, null, Map.class);
        assertTrue(((Number) tetouan.getBody().get("totalElements")).intValue() > 0,
                "filtre Tetouan/Tétouan attendu > 0, got " + tetouan.getBody());

        System.out.println("PERF n=" + n + " rebuildMs=" + rebuildMs + " pageMs=" + pageMs + " filterMs=" + filterMs);
        assertTrue(rebuildMs < 60_000, "rebuild trop lent: " + rebuildMs);
        assertTrue(pageMs < 5_000, "page trop lente: " + pageMs);
        assertTrue(filterMs < 5_000, "filtre trop lent: " + filterMs);
    }

    private ApiDtos.JobOfferRequest newOfferReq(String title, String location, String contract, String skills, boolean published) {
        return new ApiDtos.JobOfferRequest(
                title,
                "Description suffisamment longue pour " + title + " afin de passer les validations metier.",
                skills,
                location,
                contract,
                7,
                published,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                WorkMode.HYBRID, null, null, null, null, null, null
        );
    }

    private User compte(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-audit-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        user.setFullName("Audit");
        user.setFirstName("Audit");
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        return users.save(user);
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "audit")).session().getId().toString());
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}