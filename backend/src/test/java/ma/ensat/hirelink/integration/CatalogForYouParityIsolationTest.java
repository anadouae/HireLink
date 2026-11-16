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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clôture vague 1 — parité OFF/ON + présence Web éphémère.
 * Base hirelink_foryou_parity_iso — hors démo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_foryou_parity_iso?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
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
        "hirelink.web-search.max-results=10",
        "hirelink.catalog.rebuild-on-startup=false",
        "hirelink.catalog.for-you-threshold=50",
        "hirelink.jwt.secret=hirelink-foryou-parity-iso-key-not-for-real-use-0",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
class CatalogForYouParityIsolationTest {

    private static final AtomicBoolean WEB_ON = new AtomicBoolean(false);

    @TestConfiguration
    static class WebToggle {
        @Bean
        @Primary
        WebJobDiscovery web() {
            return (query, countryCode, location, limit) -> {
                if (!WEB_ON.get() || limit <= 0) {
                    return WebJobDiscovery.DiscoveryResult.unavailable(DisabledWebJobDiscovery.MESSAGE);
                }
                return WebJobDiscovery.DiscoveryResult.ok(List.of(
                        new WebJobDiscovery.DiscoveredJob(
                                "Remote Java Engineer",
                                "WebCorp",
                                "Worldwide",
                                null,
                                null,
                                "CDI",
                                "REMOTE",
                                "Java",
                                "Ephemeral parity test",
                                "https://example.com/jobs/remote-java-parity",
                                "https://example.com/jobs/remote-java-parity",
                                "REMOTIVE",
                                "Remotive",
                                "https://remotive.com"
                        )
                ), List.of(new WebJobDiscovery.SourceReport("REMOTIVE", "Remotive", "OK", "test", 1)),
                        "1 annonce Web temporaire via Remotive.");
            };
        }
    }

    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository orgs;
    @Autowired PasswordEncoder encoder;

    private User candidate;

    @BeforeEach
    void seed() {
        WEB_ON.set(false);
        listings.deleteAll();
        jobOffers.deleteAll();

        Organization org = orgs.findAll().stream().findFirst().orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Parity Org");
            o.setPlanTier(PlanTier.PREMIUM);
            return orgs.save(o);
        });
        User rec = users.findByEmailIgnoreCase("parity-rec@hirelink.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("parity-rec@hirelink.test");
            u.setFullName("Parity Rec");
            u.setFirstName("P");
            u.setLastName("Rec");
            u.setRole(Role.RECRUTEUR);
            u.setPasswordHash(encoder.encode("Recruteur123!"));
            u.setEmailVerified(true);
            u.setActive(true);
            return users.save(u);
        });
        candidate = users.findByEmailIgnoreCase("parity-cand@hirelink.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("parity-cand@hirelink.test");
            u.setFullName("Parity Cand");
            u.setFirstName("P");
            u.setLastName("Cand");
            u.setRole(Role.CANDIDAT);
            u.setPasswordHash(encoder.encode("Candidat123!"));
            u.setEmailVerified(true);
            u.setActive(true);
            u.setSkills("Java,Spring");
            return users.save(u);
        });
        candidate.setSkills("Java,Spring");
        candidate = users.save(candidate);

        saveOffer(org, rec, "Java Dev", "Java,Spring", "Casablanca");
        saveOffer(org, rec, "Comptable", "Comptabilité", "Rabat");
    }

    private void saveOffer(Organization org, User rec, String title, String skills, String city) {
        JobOffer offer = new JobOffer();
        offer.setOrganization(org);
        offer.setRecruiter(rec);
        offer.setTitle(title);
        offer.setDescription(title);
        offer.setRequiredSkills(skills);
        offer.setContractType("CDI");
        offer.setWorkMode(WorkMode.ONSITE);
        offer.setLocation(city);
        offer.setPublished(true);
        offer = jobOffers.save(offer);
        CatalogListing row = new CatalogListing();
        row.setOrigin(CatalogListing.ORIGIN_INTERNAL);
        row.setRefId(offer.getId());
        row.setPublicId(String.valueOf(offer.getId()));
        row.setSourceCode("HIRELINK");
        row.setTitle(title);
        row.setCompanyName(org.getName());
        row.setLocationLabel(city);
        row.setCity(city);
        row.setCityFold(city.toLowerCase());
        row.setCountryCode("MA");
        row.setContractLabel("CDI");
        row.setWorkMode("ONSITE");
        row.setSkillsText(skills);
        row.setDescription(title);
        row.setLinkReliable(true);
        row.setPublishedTrusted(true);
        row.setPublishedAt(Instant.now());
        row.setSortAt(Instant.now());
        row.setSearchBlob(title + " " + skills);
        listings.save(row);
    }

    @Test
    void webEphemerePresentOffEtOn() {
        WEB_ON.set(true);
        CatalogOfferService.CatalogPage off = catalog.search(
                candidate, null, null, null, null, null, null, "all", null, false, null, 0, 20);
        CatalogOfferService.CatalogPage on = catalog.search(
                candidate, null, null, null, null, null, null, "all", null, true, null, 0, 20);
        assertTrue(off.items().stream().anyMatch(i -> Boolean.TRUE.equals(i.get("ephemeral"))), "OFF garde le Web");
        assertTrue(on.items().stream().anyMatch(i -> Boolean.TRUE.equals(i.get("ephemeral"))), "ON garde le Web");
        Set<String> offCat = catalogIds(off.items());
        Set<String> onCat = catalogIds(on.items());
        assertEquals(offCat, onCat);
        assertFalse(String.valueOf(on.webSearchCoverage()).matches("(?s).*Sources prêtes.*"));
        assertFalse(String.valueOf(on.webSearchCoverage()).matches("(?s).*=\\s*OK\\(.*"));
    }

    @Test
    void totauxCatalogueStablesMalgreWeb() {
        WEB_ON.set(true);
        CatalogOfferService.CatalogPage off = catalog.search(
                candidate, null, null, null, null, null, null, "all", null, false, null, 0, 20);
        CatalogOfferService.CatalogPage on = catalog.search(
                candidate, null, null, null, null, null, null, "all", null, true, null, 0, 20);
        assertEquals(2, off.totalElements(), "total = catalogue uniquement (pas gonflé par le Web)");
        assertEquals(off.totalElements(), on.totalElements());
        assertTrue(off.items().stream().anyMatch(i -> Boolean.TRUE.equals(i.get("ephemeral"))));
        assertTrue(on.items().stream().anyMatch(i -> Boolean.TRUE.equals(i.get("ephemeral"))));
        // Pagination : page suivante sans Web doit encore exposer le reste du catalogue
        CatalogOfferService.CatalogPage offP1 = catalog.search(
                candidate, null, null, null, null, null, null, "all", null, false, null, 1, 1);
        CatalogOfferService.CatalogPage onP1 = catalog.search(
                candidate, null, null, null, null, null, null, "all", null, true, null, 1, 1);
        assertEquals(1, offP1.items().stream().filter(i -> !Boolean.TRUE.equals(i.get("ephemeral"))).count());
        assertEquals(1, onP1.items().stream().filter(i -> !Boolean.TRUE.equals(i.get("ephemeral"))).count());
        Set<String> allOff = new java.util.HashSet<>(catalogIds(off.items()));
        allOff.addAll(catalogIds(offP1.items()));
        Set<String> allOn = new java.util.HashSet<>(catalogIds(on.items()));
        allOn.addAll(catalogIds(onP1.items()));
        assertEquals(allOff, allOn);
        assertEquals(2, allOff.size());
    }

    private static Set<String> catalogIds(List<Map<String, Object>> items) {
        return items.stream()
                .filter(i -> !Boolean.TRUE.equals(i.get("ephemeral")))
                .map(i -> String.valueOf(i.get("id")))
                .collect(Collectors.toSet());
    }
}
