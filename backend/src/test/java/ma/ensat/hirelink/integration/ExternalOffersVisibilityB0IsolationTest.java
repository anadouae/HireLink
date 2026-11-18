package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.CatalogListingIndexer;
import ma.ensat.hirelink.service.CatalogOfferService;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.ExternalOfferListability;
import ma.ensat.hirelink.service.ExternalSourceCatalog;
import ma.ensat.hirelink.service.ParsedExternalJob;
import ma.ensat.hirelink.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B0 backend — alignement {@code GET /external-offers} sur la politique catalogue.
 * Base isolée {@code hirelink_b0_backend} (create-drop). Jamais la base {@code hirelink}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Pipeline d'import conservé (désactivé par défaut) : ce test vérifie son comportement quand il est rétabli.
        "hirelink.catalog.include-imported=true",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_b0_backend?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
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
        "hirelink.catalog.exclude-sources=MAROC_EMPLOI",
        "hirelink.jwt.secret=hirelink-b0-backend-isolation-test-key-not-for-real-use-00",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
class ExternalOffersVisibilityB0IsolationTest {

    @Autowired TestRestTemplate http;
    @Autowired ExternalJobOfferRepository externalOffers;
    @Autowired ExternalJobImportService imports;
    @Autowired CatalogListingIndexer indexer;
    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired ExternalOfferListability listability;
    @Autowired ExternalSourceCatalog sources;
    @Autowired Environment env;

    @Value("${spring.datasource.url}")
    String datasourceUrl;

    @BeforeEach
    void clean() {
        listings.deleteAll();
        externalOffers.deleteAll();
        jobOffers.deleteAll();
    }

    @Test
    void isolationRefuseBaseDemoEtConnecteursDesactives() {
        assertTrue(datasourceUrl.contains("hirelink_b0_backend"));
        assertFalse(datasourceUrl.contains("/hirelink?"));
        assertFalse(datasourceUrl.matches(".*/hirelink(&|$).*"));
        assertTrue(listability.excludeSources().contains("MAROC_EMPLOI"));
        assertFalse(sources.all().stream().anyMatch(s -> s.enabled()));
        assertEquals("false", env.getProperty("hirelink.external-jobs.startup-sync"));
        assertEquals("false", env.getProperty("hirelink.external-jobs.maroc.enabled"));
        assertEquals("false", env.getProperty("hirelink.external-jobs.remoteok.enabled"));
        assertEquals("false", env.getProperty("hirelink.external-jobs.jooble.enabled"));
    }

    @Test
    void externalOffersExclutMarocEmploiMaisGardeSourceAutorisee() {
        imports.importSnapshot(ExternalSourceCatalog.MAROC_EMPLOI, List.of(
                deep("m1", "Dev Maroc", "FixtureCo", "https://jobs.lever.co/fixture/abcdef12-3456-7890-abcd-ef1234567890", "Casablanca")));
        imports.importSnapshot(ExternalSourceCatalog.REMOTE_OK, List.of(
                deep("r1", "Dev Remote", "Acme", "https://remoteok.com/remote-jobs/r1", "Remote")));

        assertEquals(2, externalOffers.count());
        ExternalJobOffer maroc = externalOffers.findBySource(ExternalSourceCatalog.MAROC_EMPLOI).get(0);
        ExternalJobOffer remote = externalOffers.findBySource(ExternalSourceCatalog.REMOTE_OK).get(0);
        assertTrue(maroc.isAvailable());
        assertTrue(remote.isAvailable());
        assertFalse(listability.isListable(maroc));
        assertTrue(listability.isListable(remote));

        User candidate = compte(Role.CANDIDAT);
        ResponseEntity<List> list = http.exchange("/api/v1/external-offers", HttpMethod.GET, auth(token(candidate)), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        @SuppressWarnings("unchecked")
        Set<String> titles = ((List<Object>) list.getBody()).stream()
                .map(o -> String.valueOf(((Map<?, ?>) o).get("title")))
                .collect(Collectors.toSet());
        assertTrue(titles.contains("Dev Remote"));
        assertFalse(titles.contains("Dev Maroc"));
        @SuppressWarnings("unchecked")
        Set<String> src = ((List<Object>) list.getBody()).stream()
                .map(o -> String.valueOf(((Map<?, ?>) o).get("source")))
                .collect(Collectors.toSet());
        assertFalse(src.contains("MAROC_EMPLOI"));
        assertTrue(src.contains("REMOTE_OK"));
    }

    @Test
    void catalogueEtExternalOffersRestentAlignesSurExcludeSources() {
        imports.importSnapshot(ExternalSourceCatalog.MAROC_EMPLOI, List.of(
                deep("m2", "Exclu", "X", "https://jobs.lever.co/x/abcdef12-3456-7890-abcd-ef1234567890", "Rabat")));
        imports.importSnapshot(ExternalSourceCatalog.REMOTE_OK, List.of(
                deep("r2", "Autorise", "Y", "https://remoteok.com/remote-jobs/r2", "Casablanca")));

        User recruiter = compte(Role.RECRUTEUR);
        JobOffer internal = new JobOffer();
        internal.setRecruiter(recruiter);
        internal.setTitle("Interne Java");
        internal.setDescription("Offre interne de non-regression catalogue B0.");
        internal.setLocation("Casablanca");
        internal.setContractType("CDI");
        internal.setRequiredSkills("Java");
        internal.setPublished(true);
        jobOffers.save(internal);

        int kept = indexer.rebuild();
        assertTrue(kept >= 2);

        // null country = pas de filtre pays (équivalent controller ALL)
        CatalogOfferService.CatalogPage page = catalog.search(
                null, null, null, null, null, null, null, null, null, false, null, 0, 50);
        Set<String> catalogTitles = page.items().stream()
                .map(m -> String.valueOf(m.get("title")))
                .collect(Collectors.toSet());
        assertTrue(catalogTitles.contains("Interne Java"));
        assertTrue(catalogTitles.contains("Autorise"));
        assertFalse(catalogTitles.contains("Exclu"));

        ResponseEntity<List> ext = http.exchange("/api/v1/external-offers", HttpMethod.GET, auth(token(compte(Role.CANDIDAT))), List.class);
        assertEquals(HttpStatus.OK, ext.getStatusCode());
        @SuppressWarnings("unchecked")
        Set<String> extTitles = ((List<Object>) ext.getBody()).stream()
                .map(o -> String.valueOf(((Map<?, ?>) o).get("title")))
                .collect(Collectors.toSet());
        assertTrue(extTitles.contains("Autorise"));
        assertFalse(extTitles.contains("Exclu"));

        CatalogOfferService.CatalogPage filtered = catalog.search(
                null, "Java", "MA", null, null, null, null, "internal", null, false, null, 0, 20);
        assertTrue(filtered.totalElements() >= 1);
        assertTrue(filtered.items().stream().allMatch(m ->
                "INTERNAL".equalsIgnoreCase(String.valueOf(m.get("origin")))
                        || Boolean.FALSE.equals(m.get("external"))));
    }

    @Test
    void quarantaineEtMasquageEtHomeRestentHorsVitrine() {
        ExternalJobOffer q = raw("REMOTE_OK", "q1", "Quarantine", true, true, false, "DEEP");
        ExternalJobOffer h = raw("REMOTE_OK", "h1", "Hidden", true, false, true, "DEEP");
        ExternalJobOffer home = raw("REMOTE_OK", "home1", "HomeOnly", true, false, false, "GENERIC_HOME");
        ExternalJobOffer ok = raw("REMOTE_OK", "ok1", "OkDeep", true, false, false, "DEEP");
        externalOffers.saveAll(List.of(q, h, home, ok));

        ResponseEntity<List> list = http.exchange("/api/v1/external-offers", HttpMethod.GET,
                auth(token(compte(Role.CANDIDAT))), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        @SuppressWarnings("unchecked")
        Set<String> titles = ((List<Object>) list.getBody()).stream()
                .map(o -> String.valueOf(((Map<?, ?>) o).get("title")))
                .collect(Collectors.toSet());
        assertEquals(Set.of("OkDeep"), titles);
    }

    @Test
    void syncDesactiveeRenvoieConflitSansActiverConnecteurs() {
        User admin = compte(Role.ADMINISTRATEUR);
        ResponseEntity<String> sync = http.exchange("/api/v1/external-offers/sync", HttpMethod.POST,
                auth(token(admin)), String.class);
        assertEquals(HttpStatus.CONFLICT, sync.getStatusCode());
        assertTrue(sync.getBody() != null && sync.getBody().toLowerCase().contains("desactiv"));
    }

    private ParsedExternalJob deep(String id, String title, String company, String url, String location) {
        return new ParsedExternalJob(id, title, company, url, null, location, "CDI", "desc", "Java",
                null, null, Instant.now());
    }

    private ExternalJobOffer raw(String source, String extId, String title, boolean available,
                                 boolean quarantined, boolean hidden, String quality) {
        Instant now = Instant.now();
        ExternalJobOffer o = new ExternalJobOffer();
        o.setSource(source);
        o.setExternalId(extId);
        o.setTitle(title);
        o.setCompany("Co");
        o.setOriginUrl("https://example.com/jobs/" + extId);
        o.setAvailable(available);
        o.setQuarantined(quarantined);
        o.setHiddenByAdmin(hidden);
        o.setUrlQuality(quality);
        o.setPublishedAt(now);
        o.setLastSyncedAt(now);
        o.setLastSeenAt(now);
        return o;
    }

    private User compte(Role role) {
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "-b0-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        u.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        u.setFullName("B0 " + role.name());
        u.setFirstName("B0");
        u.setRole(role);
        u.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        u.setEmailVerified(true);
        u.setProfileComplete(true);
        return users.save(u);
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "b0")).session().getId().toString());
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }
}
