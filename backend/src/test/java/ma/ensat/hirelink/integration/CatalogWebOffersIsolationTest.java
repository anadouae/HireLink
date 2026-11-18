package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.CatalogListingIndexer;
import ma.ensat.hirelink.service.CatalogOfferService;
import ma.ensat.hirelink.service.WebJobDiscovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Affichage progressif (/catalog/offers sans attendre le Web, puis /catalog/web-offers), doublons HireLink / web,
 * libellés de contrat en français, date des offres web et choix « Non précisé ». Base isolée {@code hirelink_weboffers}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_weboffers?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "hirelink.seed.enabled=false",
        "hirelink.scheduling.enabled=false",
        "hirelink.web-search.enabled=false",
        "hirelink.external-jobs.startup-sync=false",
        "hirelink.external-jobs.maroc.enabled=false",
        "hirelink.external-jobs.remoteok.enabled=false",
        "hirelink.external-jobs.jooble.enabled=false",
        "hirelink.jwt.secret=hirelink-weboffers-isolation-test-key-not-for-real-use-0000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
class CatalogWebOffersIsolationTest {

    @TestConfiguration
    static class FakeWeb {
        @Bean
        @Primary
        WebJobDiscovery fakeWebDiscovery() {
            Instant recent = Instant.now().minus(Duration.ofDays(2));
            return (query, countryCode, location, limit) -> WebJobDiscovery.DiscoveryResult.ok(List.of(
                    // Même offre que l'offre HireLink : retirée (HireLink d'abord).
                    web("Développeur Java", "Acme Recrutement", "Casablanca", "CDI", "https://web.example/jobs/1", null),
                    web("Développeur Python", "Delta", "Remote", "full_time", "https://web.example/jobs/2", recent),
                    web("Développeur Go", "Epsilon", "Remote", null, "https://web.example/jobs/3", null)));
        }

        private static WebJobDiscovery.DiscoveredJob web(String title, String company, String loc, String contract,
                                                         String url, Instant published) {
            return new WebJobDiscovery.DiscoveredJob(title, company, loc, loc, null, contract, "REMOTE", "Java", null,
                    url, url, "WEB_TEST", "Web test", "https://web.example").withDetails(published, null, null, null);
        }
    }

    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingIndexer indexer;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        listings.deleteAll();
        jobOffers.deleteAll();
        User recruiter = new User();
        recruiter.setEmail("rec-web-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        recruiter.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        recruiter.setFullName("Acme Recrutement");
        recruiter.setFirstName("Acme");
        recruiter.setRole(Role.RECRUTEUR);
        recruiter.setPlanTier(PlanTier.ACCES);
        recruiter.setEmailVerified(true);
        users.save(recruiter);
        offer(recruiter, "Développeur Java", "CDI");
        offer(recruiter, "Développeur PHP", null);
        indexer.rebuild();
    }

    private void offer(User recruiter, String title, String contract) {
        JobOffer o = new JobOffer();
        o.setRecruiter(recruiter);
        o.setTitle(title);
        o.setDescription("Offre interne.");
        o.setLocation("Casablanca");
        o.setContractType(contract);
        o.setRequiredSkills("Java");
        o.setPublished(true);
        jobOffers.save(o);
    }

    private static Set<String> titles(List<Map<String, Object>> items) {
        return items.stream().map(m -> String.valueOf(m.get("title"))).collect(Collectors.toSet());
    }

    private CatalogOfferService.CatalogPage hirelink(String q, String contract) {
        return catalog.search(null, q, null, null, null, contract, null, null, null, null, null, null, null, null,
                false, null, null, true, true, 0, 20);
    }

    private CatalogOfferService.WebOffersPage web(String contract, Integer sinceDays, String sort) {
        return catalog.searchWebOffers("développeur", null, null, null, contract, null, null, sinceDays, null, null,
                null, null, sort, true);
    }

    @Test
    void offresHireLinkSansAttendreLeWeb() {
        CatalogOfferService.CatalogPage page = hirelink("développeur", null);
        assertEquals("DEFERRED", page.webSearchStatus());
        assertEquals(Set.of("Développeur Java", "Développeur PHP"), titles(page.items()));
        assertTrue(page.items().stream().noneMatch(m -> Boolean.TRUE.equals(m.get("external"))));
    }

    @Test
    void offresWebDedoublonneesContratEnFrancaisEtDate() {
        CatalogOfferService.WebOffersPage w = web(null, null, null);
        assertEquals(Set.of("Développeur Python", "Développeur Go"), titles(w.items()), "doublon HireLink retiré");
        Map<String, Object> python = w.items().stream()
                .filter(m -> "Développeur Python".equals(m.get("title"))).findFirst().orElseThrow();
        assertEquals("Temps plein", python.get("contractType"));
        assertTrue(python.get("publishedAt") != null, "date de la source transmise");
        @SuppressWarnings("unchecked")
        Map<String, Integer> contracts = (Map<String, Integer>) w.facets().get("contract");
        assertEquals(1, contracts.get("Temps plein"));
        assertEquals(1, contracts.get(CatalogOfferService.UNSPECIFIED));

        assertEquals(Set.of("Développeur Python"), titles(web(null, 7, null).items()), "filtre Date : offre datée");
        assertEquals(Set.of("Développeur Go"), titles(web("NON-PRECISE", null, null).items()));
        assertEquals("Développeur Python", web(null, null, "date").items().get(0).get("title"),
                "tri par date : offres datées d'abord");
    }

    @Test
    void choixNonPreciseSurLesOffresHireLink() {
        CatalogOfferService.CatalogPage all = hirelink(null, null);
        @SuppressWarnings("unchecked")
        Map<String, Integer> contracts = (Map<String, Integer>) all.facets().get("contract");
        assertEquals(1, contracts.get(CatalogOfferService.UNSPECIFIED), "offre sans contrat comptée");
        assertEquals(Set.of("Développeur PHP"), titles(hirelink(null, "NON-PRECISE").items()), "chemin SQL");
        assertEquals(Set.of("Développeur PHP"), titles(hirelink("développeur", "NON-PRECISE").items()), "recherche libre");
        assertFalse(titles(hirelink(null, "CDI").items()).contains("Développeur PHP"));
    }
}
