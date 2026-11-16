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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vague 1 — forYou classe sans masquer les scores faibles ; profil vide reste utilisable.
 * Base dédiée hirelink_foryou_nohide_iso — ne touche ni 8081 ni hirelink.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_foryou_nohide_iso?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
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
        "hirelink.catalog.rebuild-on-startup=false",
        "hirelink.catalog.for-you-threshold=50",
        "hirelink.jwt.secret=hirelink-foryou-nohide-iso-key-not-for-real-use-00",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
class CatalogForYouNoHideIsolationTest {

    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository orgs;
    @Autowired PasswordEncoder encoder;

    private User candidateWithSkills;
    private User candidateEmpty;
    private long highOfferId;
    private long lowOfferId;

    @BeforeEach
    void seed() {
        listings.deleteAll();
        jobOffers.deleteAll();

        Organization org = orgs.findAll().stream().findFirst().orElseGet(() -> {
            Organization o = new Organization();
            o.setName("ForYou Org");
            o.setPlanTier(PlanTier.PREMIUM);
            return orgs.save(o);
        });

        User recruiter = users.findByEmailIgnoreCase("foryou-rec@hirelink.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("foryou-rec@hirelink.test");
            u.setFullName("ForYou Rec");
            u.setFirstName("For");
            u.setLastName("You");
            u.setRole(Role.RECRUTEUR);
            u.setPasswordHash(encoder.encode("Recruteur123!"));
            u.setEmailVerified(true);
            u.setActive(true);
            return users.save(u);
        });

        candidateWithSkills = users.findByEmailIgnoreCase("foryou-cand@hirelink.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("foryou-cand@hirelink.test");
            u.setFullName("Cand Skills");
            u.setFirstName("Cand");
            u.setLastName("Skills");
            u.setRole(Role.CANDIDAT);
            u.setPasswordHash(encoder.encode("Candidat123!"));
            u.setEmailVerified(true);
            u.setActive(true);
            u.setSkills("Java,Spring,React");
            u.setCvSections("{\"skills\":\"Java,Spring,React\"}");
            return users.save(u);
        });
        candidateWithSkills.setSkills("Java,Spring,React");
        candidateWithSkills.setCvSections("{\"skills\":\"Java,Spring,React\"}");
        candidateWithSkills = users.save(candidateWithSkills);

        candidateEmpty = users.findByEmailIgnoreCase("foryou-empty@hirelink.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("foryou-empty@hirelink.test");
            u.setFullName("Cand Empty");
            u.setFirstName("Cand");
            u.setLastName("Empty");
            u.setRole(Role.CANDIDAT);
            u.setPasswordHash(encoder.encode("Candidat123!"));
            u.setEmailVerified(true);
            u.setActive(true);
            u.setSkills("");
            u.setCvSections("");
            return users.save(u);
        });
        candidateEmpty.setSkills("");
        candidateEmpty.setCvSections("");
        candidateEmpty = users.save(candidateEmpty);

        highOfferId = saveOffer(org, recruiter, "Ingénieur Java Spring", "Java,Spring,React", "Casablanca");
        lowOfferId = saveOffer(org, recruiter, "Assistant comptable", "Comptabilité,Excel", "Rabat");
    }

    private long saveOffer(Organization org, User recruiter, String title, String skills, String city) {
        JobOffer offer = new JobOffer();
        offer.setOrganization(org);
        offer.setRecruiter(recruiter);
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
        row.setSearchBlob(title + " " + skills + " " + city);
        listings.save(row);
        return offer.getId();
    }

    @Test
    void forYouNeMasquePasLesScoresFaibles() {
        CatalogOfferService.CatalogPage page = catalog.search(
                candidateWithSkills, null, "MA", null, null, null, null, "all", null, true, null, 0, 20);
        assertEquals(2, page.totalElements(), "les deux offres restent visibles");
        assertEquals(2, page.items().size());
        List<Map<String, Object>> items = page.items();
        // Tri décroissant par score
        Double first = scoreOf(items.get(0));
        Double second = scoreOf(items.get(1));
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first >= second);
        assertTrue(items.stream().anyMatch(i -> String.valueOf(i.get("id")).equals(String.valueOf(highOfferId))));
        assertTrue(items.stream().anyMatch(i -> String.valueOf(i.get("id")).equals(String.valueOf(lowOfferId))));
        assertTrue(items.stream().allMatch(i -> {
            String label = String.valueOf(i.get("matchLabel"));
            return !label.toLowerCase().contains("probabilité") || label.contains("pas une");
        }));
    }

    @Test
    void forYouProfilVideListeQuandMeme() {
        CatalogOfferService.CatalogPage page = catalog.search(
                candidateEmpty, null, "MA", null, null, null, null, "all", null, true, null, 0, 20);
        assertTrue(page.totalElements() >= 2, "profil vide ne doit pas vider le catalogue");
        assertFalse(page.items().isEmpty());
    }

    @Test
    void forYouConserveLesMemesOffresCatalogueQueOff() {
        CatalogOfferService.CatalogPage off = catalog.search(
                candidateWithSkills, null, "MA", null, null, null, null, "all", null, false, null, 0, 50);
        CatalogOfferService.CatalogPage on = catalog.search(
                candidateWithSkills, null, "MA", null, null, null, null, "all", null, true, null, 0, 50);
        var offIds = off.items().stream()
                .filter(i -> !Boolean.TRUE.equals(i.get("ephemeral")))
                .map(i -> String.valueOf(i.get("id")))
                .collect(java.util.stream.Collectors.toSet());
        var onIds = on.items().stream()
                .filter(i -> !Boolean.TRUE.equals(i.get("ephemeral")))
                .map(i -> String.valueOf(i.get("id")))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(offIds, onIds, "mêmes offres catalogue accessibles OFF et ON");
        assertTrue(on.items().stream().anyMatch(i -> {
            Object s = i.get("matchScore");
            return s == null || ((Number) s).doubleValue() < 50;
        }) || on.totalElements() >= 2, "aucune exclusion implicite à 50 %");
    }

    @Test
    void minMatchExplicitePeutFiltrer() {
        CatalogOfferService.CatalogPage page = catalog.search(
                candidateWithSkills, null, "MA", null, null, null, null, "all", null, false, 90.0, 0, 20);
        // Seul le filtre minMatch explicite peut réduire ; on ne vérifie que la cohérence pagination.
        assertTrue(page.totalElements() <= 2);
        assertEquals(page.totalElements(), page.items().size());
        page.items().forEach(i -> {
            Number s = (Number) i.get("matchScore");
            if (s != null) assertTrue(s.doubleValue() >= 90.0 - 0.01 || page.totalElements() == 0);
        });
    }

    private static Double scoreOf(Map<String, Object> item) {
        Object s = item.get("matchScore");
        return s == null ? 0.0 : ((Number) s).doubleValue();
    }
}
