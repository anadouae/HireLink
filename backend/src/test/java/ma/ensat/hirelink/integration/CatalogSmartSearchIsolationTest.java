package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.CatalogListing;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.WorkMode;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.CatalogOfferService;
import ma.ensat.hirelink.service.DisabledWebJobDiscovery;
import ma.ensat.hirelink.service.WebJobDiscovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolation — recherche unifiée fuzzy + synonymes + fallback Web.
 * Base dédiée ; ne touche ni 8081 ni la démo hirelink.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Pipeline d'import conservé (désactivé par défaut) : ce test vérifie son comportement quand il est rétabli.
        "hirelink.catalog.include-imported=true",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_smart_search_iso?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
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
        "hirelink.web-search.enabled=false",
        "hirelink.jwt.secret=hirelink-smart-search-iso-key-not-for-real-use-0000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
class CatalogSmartSearchIsolationTest {

    private static final AtomicBoolean WEB_ON = new AtomicBoolean(false);

    @TestConfiguration
    static class WebToggleConfig {
        @Bean
        @Primary
        WebJobDiscovery toggleableWebDiscovery() {
            return (query, countryCode, location, limit) -> {
                if (!WEB_ON.get()) {
                    return WebJobDiscovery.DiscoveryResult.unavailable(DisabledWebJobDiscovery.MESSAGE);
                }
                if (query == null || query.isBlank()) {
                    return WebJobDiscovery.DiscoveryResult.ok(List.of());
                }
                return WebJobDiscovery.DiscoveryResult.ok(List.of(
                        new WebJobDiscovery.DiscoveredJob(
                                "Senior Java Developer",
                                "Open Source Corp",
                                "Casablanca · Remote",
                                "Casablanca",
                                "MA",
                                "CDI",
                                "REMOTE",
                                "Java,Spring,Kafka",
                                "Annonce réelle de test isolation — ne pas persister.",
                                "https://example.com/jobs/senior-java-dev-iso",
                                "https://example.com/jobs/senior-java-dev-iso",
                                "WEB_TEST",
                                "Web test",
                                "https://example.com"
                        )
                ));
            };
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository orgs;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        WEB_ON.set(false);
        listings.deleteAll();
        jobOffers.deleteAll();

        Organization org = orgs.findAll().stream().findFirst().orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Smart Search Org");
            o.setPlanTier(PlanTier.PREMIUM);
            return orgs.save(o);
        });

        User recruiter = users.findAll().stream()
                .filter(u -> "smart-rec@hirelink.test".equalsIgnoreCase(u.getEmail()))
                .findFirst()
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("smart-rec@hirelink.test");
                    u.setFullName("Smart Recruiter");
                    u.setFirstName("Smart");
                    u.setLastName("Recruiter");
                    u.setRole(Role.RECRUTEUR);
                    u.setPasswordHash(encoder.encode("Recruteur123!"));
                    u.setEmailVerified(true);
                    u.setActive(true);
                    return users.save(u);
                });

        JobOffer internal = new JobOffer();
        internal.setOrganization(org);
        internal.setRecruiter(recruiter);
        internal.setTitle("Développeur Full-Stack Java — produit ATS");
        internal.setDescription("Java Spring React pour un ATS marocain.");
        internal.setRequiredSkills("Java,Spring,React");
        internal.setContractType("CDI");
        internal.setWorkMode(WorkMode.HYBRID);
        internal.setLocation("Casablanca");
        internal.setPublished(true);
        internal = jobOffers.save(internal);

        CatalogListing row = new CatalogListing();
        row.setOrigin(CatalogListing.ORIGIN_INTERNAL);
        row.setRefId(internal.getId());
        row.setPublicId(String.valueOf(internal.getId()));
        row.setSourceCode("HIRELINK");
        row.setTitle(internal.getTitle());
        row.setCompanyName(org.getName());
        row.setLocationLabel("Casablanca · Hybride");
        row.setCity("Casablanca");
        row.setCityFold("casablanca");
        row.setCountryCode("MA");
        row.setContractLabel("CDI");
        row.setWorkMode("HYBRID");
        row.setSkillsText(internal.getRequiredSkills());
        row.setDescription(internal.getDescription());
        row.setLinkReliable(true);
        row.setPublishedTrusted(true);
        row.setPublishedAt(Instant.now());
        row.setSortAt(Instant.now());
        row.setSearchBlob(internal.getTitle() + " " + org.getName() + " " + internal.getRequiredSkills()
                + " developpeur full stack java");
        listings.save(row);

        CatalogListing ext = new CatalogListing();
        ext.setOrigin(CatalogListing.ORIGIN_EXTERNAL);
        ext.setRefId(9001L);
        ext.setPublicId("ext-9001");
        ext.setSourceCode("REMOTE_OK");
        ext.setTitle("Marketing Growth Manager");
        ext.setCompanyName("Growth Labs");
        ext.setLocationLabel("Rabat");
        ext.setCity("Rabat");
        ext.setCityFold("rabat");
        ext.setCountryCode("MA");
        ext.setContractLabel("CDI");
        ext.setWorkMode("REMOTE");
        ext.setSkillsText("SEO,SEA,Analytics");
        ext.setDescription("Growth marketing digital");
        ext.setOriginUrl("https://example.com/jobs/growth-mgr");
        ext.setApplyUrl("https://example.com/jobs/growth-mgr");
        ext.setUrlQuality("DEEP");
        ext.setLinkReliable(true);
        ext.setPublishedTrusted(true);
        ext.setPublishedAt(Instant.now());
        ext.setSortAt(Instant.now().minusSeconds(60));
        ext.setSearchBlob(ext.getTitle() + " " + ext.getCompanyName() + " " + ext.getSkillsText()
                + " marketing growth");
        ext.setDedupeKey("dedupe-growth-mgr");
        listings.save(ext);
    }

    @Test
    void catalogueSansRequetePagine() {
        CatalogOfferService.CatalogPage page = catalog.search(
                null, null, "MA", null, null, null, null, "all", null, false, 0.0, 0, 10);
        assertTrue(page.totalElements() >= 2);
        assertFalse(page.items().isEmpty());
        assertFalse(page.webSearchAvailable());
        assertEquals("UNAVAILABLE", page.webSearchStatus());
        assertTrue(page.webSearchMessage().contains("temporairement indisponible"));
    }

    @Test
    void fauteEtAccentRetrouventOffreInterne() {
        CatalogOfferService.CatalogPage page = catalog.search(
                null, "developpeur javva", "MA", null, null, null, null, "all", null, false, 0.0, 0, 10);
        assertTrue(page.rankedByRelevance());
        assertTrue(page.totalElements() >= 1);
        assertTrue(page.items().stream().anyMatch(i ->
                String.valueOf(i.get("title")).toLowerCase().contains("java")));
        assertTrue(((Number) page.items().get(0).get("relevanceScore")).doubleValue() > 0);
        assertNotNull(page.items().get(0).get("provenance"));
    }

    @Test
    void filtresCombinesVilleEtRequete() {
        CatalogOfferService.CatalogPage page = catalog.search(
                null, "marketing", "MA", "Rabat", null, null, null, "all", null, false, 0.0, 0, 10);
        assertEquals(1, page.totalElements());
        assertEquals("ext-9001", page.items().get(0).get("id"));
        assertEquals(true, page.items().get(0).get("external"));
    }

    @Test
    void webIndisponibleNeCreePasOffrePersistee() {
        long before = listings.count();
        CatalogOfferService.CatalogPage page = catalog.search(
                null, "java developer", "MA", null, null, null, null, "all", null, false, 0.0, 0, 20);
        assertFalse(page.webSearchAvailable());
        assertEquals(before, listings.count());
        assertTrue(page.items().stream().noneMatch(i -> Boolean.TRUE.equals(i.get("ephemeral"))));
    }

    @Test
    void webActifFusionneSansPersister() {
        WEB_ON.set(true);
        long before = listings.count();
        CatalogOfferService.CatalogPage page = catalog.search(
                null, "java developer", "MA", null, null, null, null, "all", null, false, 0.0, 0, 20);
        assertTrue(page.webSearchAvailable());
        assertTrue(page.items().stream().anyMatch(i -> Boolean.TRUE.equals(i.get("ephemeral"))));
        assertEquals(before, listings.count(), "aucune persistance d'annonce Web");
        Map<String, Object> webHit = page.items().stream()
                .filter(i -> Boolean.TRUE.equals(i.get("ephemeral")))
                .findFirst().orElseThrow();
        assertEquals("https://example.com/jobs/senior-java-dev-iso", webHit.get("originUrl"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prov = (Map<String, Object>) webHit.get("provenance");
        assertEquals("ORIGIN_URL", prov.get("openMode"));
    }

    @Test
    void endpointHttpExposeMetadonnees() {
        ResponseEntity<Map> res = http.getForEntity(
                "/api/v1/catalog/offers?q=developpeur&country=MA&page=0&size=5", Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map body = res.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("webSearchAvailable"));
        assertTrue(body.containsKey("rankedByRelevance"));
        assertTrue(body.containsKey("webSearchMessage"));
    }
}
