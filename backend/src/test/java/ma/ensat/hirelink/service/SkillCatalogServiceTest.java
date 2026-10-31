package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCatalogServiceTest {

    private final SkillCatalogService catalog = new SkillCatalogService(new TextMatcher());

    @Test
    void aliasesAreResolved() {
        assertEquals("JavaScript", catalog.search("js", 5).get(0).name());
        assertEquals("Spring Boot", catalog.search("spring", 5).get(0).name());
        assertEquals("React", catalog.search("reactjs", 5).get(0).name());
        assertEquals("Node.js", catalog.search("node js", 5).get(0).name());
    }

    @Test
    void typosAreTolerated() {
        var hits = catalog.search("javscript", 5);
        assertEquals("JavaScript", hits.get(0).name());
        assertFalse(hits.get(0).exact());
        assertEquals("Python", catalog.search("pyhton", 3).get(0).name());
    }

    @Test
    void accentsAndCaseAreIgnored() {
        assertEquals("Français", catalog.search("FRANCAIS", 3).get(0).name());
        assertTrue(catalog.search("Comptabilite", 3).get(0).exact());
    }

    @Test
    void normaliseListRemovesDuplicatesAndKeepsFreeText() {
        assertEquals("JavaScript, Spring Boot, Qlik Sense avancé, Anglais (B2)",
                catalog.normaliseList("js, JavaScript, spring,  qlik Sense avancé , anglais (B2), English (C1)", 500));
    }

    @Test
    void classifyGroupsByCategory() {
        var groups = catalog.classify("Java, Docker, Anglais (B2), Esprit d'équipe, Zorglub");
        assertEquals(java.util.List.of("LANGUAGE", "DEVOPS", "SPOKEN", "SOFT", "OTHER"),
                groups.stream().map(SkillCatalogService.Group::code).toList());
        assertEquals("B2", groups.get(2).skills().get(0).level());
    }
}
