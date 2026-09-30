package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillMatchingServiceTest {

    private final SkillMatchingService service = new SkillMatchingService();

    @Test
    void perfectOverlapYieldsHighScore() {
        double score = service.computeMatchScore("Java,Spring,MySQL", "Java,Spring,MySQL");
        assertEquals(100.0, score, 0.01);
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
}
