package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMatchingServiceTest {

    private final SkillMatchingService service =
            new SkillMatchingService(new SkillCatalogService(new TextMatcher()));

    @Test
    void perfectOverlapYieldsHighScore() {
        assertEquals(100.0, service.computeMatchScore("Java,Spring,MySQL", "Java,Spring,MySQL"), 0.01);
    }

    @Test
    void partialOverlap() {
        double score = service.computeMatchScore("Java,Python", "Java,Spring,MySQL");
        assertTrue(score > 0 && score < 100);
    }

    @Test
    void emptyRequiredYieldsZero() {
        assertEquals(0.0, service.computeMatchScore("Java", ""), 0.01);
    }

    @Test
    void synonymsAndTyposAreMatched() {
        var detail = service.explain("js, spring, pyhton, Anglais (C1)", "JavaScript, Spring Boot, Python, Anglais (B2), Docker");
        assertEquals(4, detail.covered().size());
        assertEquals(java.util.List.of("Docker"), detail.missing());
        assertEquals(80.0, detail.score(), 0.01);
    }

    @Test
    void richProfileIsNotPenalised() {
        assertEquals(100.0, service.computeMatchScore("Java,Spring Boot,MySQL,Docker,Git,React,Linux,AWS", "Java,MySQL"), 0.01);
    }
}
