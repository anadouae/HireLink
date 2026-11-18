package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.CatalogListing;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.CatalogListingRepository;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.CatalogListingIndexer;
import ma.ensat.hirelink.service.CatalogOfferService;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.ExternalSourceCatalog;
import ma.ensat.hirelink.service.ParsedExternalJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recherche candidat = offres HireLink + Web en direct : les annonces importées ne sont ni indexées ni lues,
 * mais leur table reste intacte (retrait réversible). Base isolée {@code hirelink_hlonly} (create-drop).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_hlonly?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
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
        "hirelink.jwt.secret=hirelink-hlonly-isolation-test-key-not-for-real-use-000000",
        "hirelink.security.bcrypt-strength=4",
        "hirelink.swagger.enabled=false"
})
class CatalogHireLinkOnlyIsolationTest {

    @Autowired ExternalJobOfferRepository externalOffers;
    @Autowired ExternalJobImportService imports;
    @Autowired CatalogListingIndexer indexer;
    @Autowired CatalogOfferService catalog;
    @Autowired CatalogListingRepository listings;
    @Autowired JobOfferRepository jobOffers;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clean() {
        listings.deleteAll();
        externalOffers.deleteAll();
        jobOffers.deleteAll();
    }

    @Test
    void rechercheCandidatNeLitPlusLesAnnoncesImportees() {
        imports.importSnapshot(ExternalSourceCatalog.REMOTE_OK, List.of(new ParsedExternalJob(
                "r1", "Développeur importé", "GitLab", "https://remoteok.com/remote-jobs/r1", null,
                "Casablanca", "CDI", "desc", "Java", null, null, Instant.now())));

        User recruiter = new User();
        recruiter.setEmail("rec-hlonly-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        recruiter.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        recruiter.setFullName("Recruteur Test");
        recruiter.setFirstName("Recruteur");
        recruiter.setRole(Role.RECRUTEUR);
        recruiter.setPlanTier(PlanTier.ACCES);
        recruiter.setEmailVerified(true);
        users.save(recruiter);
        JobOffer internal = new JobOffer();
        internal.setRecruiter(recruiter);
        internal.setTitle("Développeur HireLink");
        internal.setDescription("Offre interne.");
        internal.setLocation("Casablanca");
        internal.setContractType("CDI");
        internal.setRequiredSkills("Java");
        internal.setPublished(true);
        jobOffers.save(internal);

        indexer.rebuild();

        assertEquals(1, externalOffers.count(), "la table des annonces importées n'est pas vidée");
        assertTrue(listings.findAll().stream().noneMatch(l -> CatalogListing.ORIGIN_EXTERNAL.equals(l.getOrigin())),
                "aucune annonce importée projetée dans le catalogue");

        for (String q : new String[] {null, "développeur"}) {
            Set<String> titles = catalog.search(null, q, null, null, null, null, null, null, null, false, null, 0, 50)
                    .items().stream().map(m -> String.valueOf(m.get("title"))).collect(Collectors.toSet());
            assertTrue(titles.contains("Développeur HireLink"));
            assertFalse(titles.contains("Développeur importé"));
        }
        // Onglet « Offres web » : aucune ligne du catalogue stocké.
        assertEquals(0, catalog.search(null, "développeur", null, null, null, null, null, "external", null, false, null, 0, 50)
                .items().stream().filter(m -> !Boolean.TRUE.equals(m.get("ephemeral"))).count());
    }
}
