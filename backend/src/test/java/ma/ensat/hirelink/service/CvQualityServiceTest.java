package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CvQualityServiceTest {

    private final CvQualityService service = new CvQualityService();

    @Test
    void emptyProfileIsTechnicalChecklistNotAtsScore() {
        User u = new User();
        u.setEmail("c@example.com");
        Map<String, Object> out = service.assess(u);
        assertEquals("TECHNICAL_CHECKLIST", out.get("kind"));
        assertTrue(String.valueOf(out.get("disclaimer")).toLowerCase().contains("ats")
                || String.valueOf(out.get("disclaimer")).toLowerCase().contains("embauche"));
        assertFalse(String.valueOf(out.get("disclaimer")).toLowerCase().contains("100/100"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) out.get("checks");
        assertFalse(checks.isEmpty());
        assertEquals(0L, ((Number) out.get("passed")).longValue());
    }

    @Test
    void filledProfilePassesContactAndSkills() {
        User u = new User();
        u.setEmail("c@example.com");
        u.setPhone("0612345678");
        u.setCity("Tétouan");
        u.setSkills("Java, Spring, SQL, Docker");
        u.setCvStorageName("x.pdf");
        u.setCvSections("{\"identity\":\"Ada Lovelace\",\"experience\":\"Ingénieure 2020-2024 missions\",\"education\":\"ENSAT\",\"skills\":\"Java\",\"languages\":\"Français\"}");
        Map<String, Object> out = service.assess(u);
        assertTrue(((Number) out.get("passed")).longValue() >= 5);
    }
}
