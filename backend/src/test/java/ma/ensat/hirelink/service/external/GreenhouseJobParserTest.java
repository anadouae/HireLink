package ma.ensat.hirelink.service.external;

import ma.ensat.hirelink.service.ParsedExternalJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GreenhouseJobParserTest {

    private final GreenhouseJobParser parser = new GreenhouseJobParser();
    private final ExternalBoardDefinition board = new ExternalBoardDefinition(
            "GREENHOUSE", "acme-gh", "Acme GH", "https://boards.greenhouse.io/acme-gh",
            "TEST", true, "fixture", "test");

    @Test
    void parseCompletEtHomepageSansApplyInventee() throws Exception {
        List<ParsedExternalJob> jobs = parser.parse(fixture("fixtures/ats/greenhouse-jobs.json"), board);
        assertEquals(2, jobs.size());
        ParsedExternalJob deep = jobs.get(0);
        assertEquals("acme-gh:127817", deep.externalId());
        assertEquals("Vault Designer", deep.title());
        assertEquals("Acme GH", deep.company());
        assertEquals("https://boards.greenhouse.io/acme-gh/jobs/127817", deep.originUrl());
        assertNull(deep.applyUrl());
        assertEquals("Casablanca, Morocco", deep.location());
        assertNotNull(deep.publishedAt());
        assertNotNull(deep.description());
        assertNull(deep.skills());
        assertNull(deep.salaryMin());
        assertEquals("acme-gh", deep.boardKey());
        assertEquals("https://www.acme-gh.example/", jobs.get(1).originUrl());
    }

    @Test
    void fluxVide() throws Exception {
        assertTrue(parser.parse(fixture("fixtures/ats/greenhouse-empty.json"), board).isEmpty());
    }

    @Test
    void malforme() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(fixture("fixtures/ats/greenhouse-malformed.json"), board));
    }

    private static String fixture(String path) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}