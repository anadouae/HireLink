package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteOkParserTest {

    private final RemoteOkParser parser = new RemoteOkParser();

    @Test
    void uneOffreCompleteIgnoreLeBandeauLegal() {
        List<RemoteOkParser.ParsedJob> jobs = parser.parse("""
                [
                  {"legal":"Please link back to Remote OK"},
                  {"id":42,"position":"Java Developer","company":"Northwind","url":"https://remoteok.com/remote-jobs/42",
                   "apply_url":"https://northwind.example/jobs/42","location":"Worldwide","tags":["java","full-time"],
                   "description":"<p>Build APIs</p>","date":"1700000000","salary_min":0,"salary_max":90000}
                ]
                """);
        assertEquals(1, jobs.size());
        RemoteOkParser.ParsedJob job = jobs.get(0);
        assertEquals("42", job.externalId());
        assertEquals("Java Developer", job.title());
        assertEquals("Northwind", job.company());
        assertEquals("https://remoteok.com/remote-jobs/42", job.originUrl());
        assertEquals("https://northwind.example/jobs/42", job.applyUrl());
        assertEquals("Worldwide", job.location());
        assertEquals("full-time", job.contractLabel());
        assertEquals("java", job.skills());
        assertEquals("Build APIs", job.description());
        RemoteOkParser.ParsedJob marked = parser.parse("""
                [{"id":43,"position":"Java","company":"Acme","url":"https://remoteok.com/remote-jobs/43",
                  "description":"<script>alert(1)</script><p>Bonjour &#39;equipe&#39;</p>"}]
                """).get(0);
        assertEquals("Bonjour 'equipe'", marked.description());
        assertTrue(marked.description().indexOf('<') < 0);
        assertTrue(marked.description().indexOf("alert") < 0);
        assertNull(job.salaryMin());
        assertEquals(90000, job.salaryMax());
        assertEquals(1700000000L, job.publishedAt().getEpochSecond());
    }

    @Test
    void unTableauVideNeCreeAucuneOffre() {
        assertTrue(parser.parse("[{\"legal\":\"terms\"}]").isEmpty());
        assertTrue(parser.parse("[]").isEmpty());
    }

    @Test
    void uneUrlInvalideEstEcartee() {
        List<RemoteOkParser.ParsedJob> jobs = parser.parse("""
                [{"id":7,"position":"Designer","company":"Acme","url":"javascript:alert(1)"}]
                """);
        assertTrue(jobs.isEmpty());
    }

    @Test
    void unFormatInattenduEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{\"jobs\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("pas du json"));
    }

    @Test
    void unChampAbsentResteAbsent() {
        RemoteOkParser.ParsedJob job = parser.parse("""
                [{"id":"abc","position":"Support","company":"Acme","url":"https://remoteok.com/remote-jobs/abc"}]
                """).get(0);
        assertNull(job.location());
        assertNull(job.contractLabel());
        assertNull(job.skills());
        assertNull(job.publishedAt());
        assertNull(job.salaryMin());
        assertEquals(job.originUrl(), job.applyUrl());
    }
}
