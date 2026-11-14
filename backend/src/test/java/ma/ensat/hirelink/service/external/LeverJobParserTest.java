package ma.ensat.hirelink.service.external;

import ma.ensat.hirelink.service.ParsedExternalJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeverJobParserTest {

    private final LeverJobParser parser = new LeverJobParser();
    private final ExternalBoardDefinition board = new ExternalBoardDefinition(
            "LEVER", "acme-lever", "Acme Lever", "https://jobs.lever.co/acme-lever",
            "TEST", true, "fixture", "test");

    @Test
    void parseAvecEtSansApplyUrl() throws Exception {
        List<ParsedExternalJob> jobs = parser.parse(fixture("fixtures/ats/lever-postings.json"), board);
        assertEquals(2, jobs.size());
        ParsedExternalJob full = jobs.get(0);
        assertEquals("acme-lever:posting-uuid-001", full.externalId());
        assertEquals("Backend Engineer", full.title());
        assertEquals("https://jobs.lever.co/acme-lever/posting-uuid-001", full.originUrl());
        assertEquals("https://jobs.lever.co/acme-lever/posting-uuid-001/apply", full.applyUrl());
        assertEquals("Full-time", full.contractLabel());
        assertNotNull(full.publishedAt());
        ParsedExternalJob noApply = jobs.get(1);
        assertNull(noApply.applyUrl());
        assertEquals(noApply.originUrl(), "https://jobs.lever.co/acme-lever/posting-no-apply");
    }

    @Test
    void malforme() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{}", board));
    }

    private static String fixture(String path) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}