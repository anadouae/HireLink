package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMatcherTest {

    private final TextMatcher matcher = new TextMatcher();

    @Test
    void foldRemovesAccentsCaseAndPunctuation() {
        assertEquals("tetouan hybride", TextMatcher.fold("  Tétouan · Hybride "));
        assertEquals("nodejs", TextMatcher.compact("Node.js"));
        assertEquals("c++", TextMatcher.compact("C++"));
    }

    @Test
    void scoresAreOrdered() {
        double exact = matcher.score("java", "Java");
        double prefix = matcher.score("jav", "Java");
        double word = matcher.score("boot", "Spring Boot");
        double substring = matcher.score("ring", "Spring Boot");
        double typo = matcher.score("javscript", "JavaScript");
        assertTrue(exact > prefix && prefix > word && word > substring && substring > typo && typo > 0);
        assertEquals(0, matcher.score("xyz", "Java"), 0);
    }

    @Test
    void shortWordsAreNotFuzzy() {
        assertEquals(0, matcher.score("jvv", "Java"), 0);
    }

    @Test
    void multiWordRequiresEveryTokenOrWholePhrase() {
        assertEquals(0, matcher.score("stage pfe", "Software Intern remote"), 0);
        assertTrue(matcher.score("stage pfe", "Stage PFE développeur") > 0);
        // Phrase compacte : full stack ↔ fullstack
        assertTrue(matcher.score("full stack", "Fullstack Engineer") > 0);
    }
}
