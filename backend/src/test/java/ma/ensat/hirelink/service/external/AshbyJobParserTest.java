package ma.ensat.hirelink.service.external;

import ma.ensat.hirelink.service.ParsedExternalJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AshbyJobParserTest {

    private final AshbyJobParser parser = new AshbyJobParser();
    private final ExternalBoardDefinition board = new ExternalBoardDefinition(
            "ASHBY", "acme-ashby", "Acme Ashby", "https://jobs.ashbyhq.com/acme-ashby",
            "TEST", true, "fixture", "test");

    @Test
    void ignoreUnlistedEtConserveChampsAbsents() throws Exception {
        List<ParsedExternalJob> jobs = parser.parse(fixture("fixtures/ats/ashby-jobs.json"), board);
        assertEquals(2, jobs.size());
        ParsedExternalJob full = jobs.stream().filter(j -> j.externalId().endsWith("ashby-job-1")).findFirst().orElseThrow();
        assertEquals("Product Engineer", full.title());
        assertEquals("MA", full.countryCode());
        assertEquals("Full-time", full.contractLabel());
        assertEquals("https://jobs.ashbyhq.com/acme-ashby/ashby-job-1", full.originUrl());
        assertEquals("https://jobs.ashbyhq.com/acme-ashby/ashby-job-1/application", full.applyUrl());
        assertTrue(jobs.stream().noneMatch(j -> j.title().contains("Secret")));
        ParsedExternalJob minimal = jobs.stream().filter(j -> j.externalId().endsWith("ashby-minimal")).findFirst().orElseThrow();
        assertNull(minimal.applyUrl());
        assertNull(minimal.contractLabel());
        assertNull(minimal.description());
        assertNull(minimal.publishedAt());
    }

    private static String fixture(String path) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}