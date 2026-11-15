package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CatalogListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogSmartRankerTest {

    private CatalogSmartRanker ranker;

    @BeforeEach
    void setUp() {
        ranker = new CatalogSmartRanker(new TextMatcher(), new JobSearchLexicon());
    }

    private CatalogListing listing(long id, String origin, String title, String company, String skills) {
        CatalogListing row = new CatalogListing();
        row.setId(id);
        row.setOrigin(origin);
        row.setRefId(id);
        row.setPublicId(String.valueOf(id));
        row.setSourceCode(CatalogListing.ORIGIN_INTERNAL.equals(origin) ? "HIRELINK" : "JOOBLE");
        row.setTitle(title);
        row.setCompanyName(company);
        row.setSkillsText(skills);
        row.setDescription(title + " " + skills);
        row.setSearchBlob(title + " " + company + " " + skills);
        row.setLinkReliable(true);
        row.setPublishedTrusted(true);
        row.setSortAt(Instant.parse("2026-11-01T10:00:00Z").plusSeconds(id));
        return row;
    }

    @Test
    void fauteOrthographeRetrouveTitre() {
        CatalogListing java = listing(1, CatalogListing.ORIGIN_INTERNAL,
                "Développeur Full-Stack Java", "Atlas Digital", "Java,Spring,React");
        CatalogListing marketing = listing(2, CatalogListing.ORIGIN_EXTERNAL,
                "Chargé de marketing digital", "Zellige", "SEO,Ads");
        List<CatalogSmartRanker.ScoredListing> ranked = ranker.rank("developpeur javva", List.of(java, marketing));
        assertFalse(ranked.isEmpty());
        assertEquals(1L, ranked.get(0).listing().getId());
        assertTrue(ranked.get(0).relevance() >= CatalogSmartRanker.MIN_SCORE);
    }

    @Test
    void phraseLibreEtSynonyme() {
        CatalogListing data = listing(3, CatalogListing.ORIGIN_EXTERNAL,
                "Data Analyst", "OCP Group", "SQL,Python,Power BI");
        CatalogListing rh = listing(4, CatalogListing.ORIGIN_INTERNAL,
                "Chargé de recrutement", "HireLink Demo", "RH,Talent");
        List<CatalogSmartRanker.ScoredListing> ranked = ranker.rank("analyste donnees SQL", List.of(data, rh));
        assertFalse(ranked.isEmpty());
        assertEquals(3L, ranked.get(0).listing().getId());
        assertTrue(ranked.get(0).relevance() > ranked.stream()
                .filter(s -> s.listing().getId() == 4L).findFirst().map(CatalogSmartRanker.ScoredListing::relevance).orElse(0.0));
    }

    @Test
    void neFavorisePasInterneSurScoreEgalOuInferieur() {
        CatalogListing internal = listing(10, CatalogListing.ORIGIN_INTERNAL,
                "Comptable junior", "Atlas", "Excel,SAP");
        CatalogListing external = listing(11, CatalogListing.ORIGIN_EXTERNAL,
                "UX Designer Product Figma", "Remote Co", "Figma,UI,UX");
        // Requête alignée sur l'externe uniquement — l'interne ne doit pas gagner par origine.
        List<CatalogSmartRanker.ScoredListing> ranked = ranker.rank("UX Designer Product Figma", List.of(internal, external));
        assertEquals(CatalogListing.ORIGIN_EXTERNAL, ranked.get(0).listing().getOrigin());
        assertTrue(ranked.get(0).relevance() >= CatalogSmartRanker.MIN_SCORE);
    }

    @Test
    void dedupeConserveMeilleurScore() {
        CatalogListing a = listing(1, CatalogListing.ORIGIN_INTERNAL,
                "Développeur Java", "Atlas Digital", "Java");
        CatalogListing b = listing(2, CatalogListing.ORIGIN_EXTERNAL,
                "Developpeur Java", "Atlas Digital", "Java,Spring");
        List<CatalogSmartRanker.ScoredListing> ranked = ranker.rank("developpeur java spring", List.of(a, b));
        List<CatalogSmartRanker.ScoredListing> deduped = ranker.dedupe(ranked);
        assertEquals(1, deduped.size());
    }
}
