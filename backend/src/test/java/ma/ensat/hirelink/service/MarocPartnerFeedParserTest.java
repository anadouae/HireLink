package ma.ensat.hirelink.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarocPartnerFeedParserTest {

    private final MarocPartnerFeedParser parser = new MarocPartnerFeedParser();

    @Test
    void litUnFluxPartenaireValide() {
        List<ParsedExternalJob> jobs = parser.parse("""
                {
                  "source":"MAROC_EMPLOI",
                  "jobs":[
                    {"id":"ma-1","title":"Dev Java","company":"Atlas","location":"Casablanca","contract":"CDI",
                     "skills":"Java, Spring","url":"https://www.rekrute.com/","applyUrl":"https://www.rekrute.com/a",
                     "salaryMin":12000,"salaryMax":18000,"publishedAt":"2026-11-10T09:00:00Z","description":"API Spring"}
                  ]
                }
                """);
        assertEquals(1, jobs.size());
        ParsedExternalJob job = jobs.get(0);
        assertEquals("ma-1", job.externalId());
        assertEquals("Dev Java", job.title());
        assertEquals("Atlas", job.company());
        assertEquals("Casablanca", job.location());
        assertEquals("CDI", job.contractLabel());
        assertEquals("Java, Spring", job.skills());
        assertEquals(12000, job.salaryMin());
        assertEquals(18000, job.salaryMax());
        assertEquals("https://www.rekrute.com/", job.originUrl());
        assertEquals("https://www.rekrute.com/a", job.applyUrl());
    }

    @Test
    void ecarteUneUrlNonHttps() {
        assertTrue(parser.parse("""
                {"jobs":[{"id":"x","title":"A","company":"B","url":"http://insecure.example/"}]}
                """).isEmpty());
    }

    @Test
    void refuseUnFormatInattendu() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("pas du json"));
    }
}
