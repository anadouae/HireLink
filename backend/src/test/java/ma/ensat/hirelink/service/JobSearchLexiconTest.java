package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobSearchLexiconTest {

    private final JobSearchLexicon lexicon = new JobSearchLexicon();

    @Test
    void expandInclutSynonymesDeveloppeur() {
        List<String> v = lexicon.expand("développeur java");
        assertTrue(v.stream().anyMatch(s -> s.contains("developpeur")));
        assertTrue(v.stream().anyMatch(s -> s.equals("developer") || s.contains("developer") || s.equals("dev")));
        assertTrue(v.stream().anyMatch(s -> s.equals("java")));
    }

    @Test
    void expandSansAccentRetrouveVariantes() {
        List<String> v = lexicon.expand("data analyste");
        assertFalse(v.isEmpty());
        assertTrue(v.stream().anyMatch(s -> s.contains("data") || s.contains("donnees")));
    }

    @Test
    void nearDuplicateMemePosteMemeEntreprise() {
        assertTrue(lexicon.nearDuplicate(
                "Développeur Full-Stack Java", "Atlas Digital",
                "Developpeur Full Stack Java", "Atlas Digital"));
        assertFalse(lexicon.nearDuplicate(
                "Développeur Full-Stack Java", "Atlas Digital",
                "Développeur Full-Stack Java", "OCP Group"));
    }

    @Test
    void significantTokensIgnoreStopWords() {
        assertEquals(List.of("stage", "pfe"), lexicon.significantTokens("stage de pfe"));
        assertTrue(lexicon.significantTokens("le développeur").contains("developpeur"));
    }
}
