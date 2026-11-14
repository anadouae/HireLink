package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.ParsedExternalJob;
import ma.ensat.hirelink.service.external.AtsHttpClient;
import ma.ensat.hirelink.service.external.ExternalBoardDefinition;
import ma.ensat.hirelink.service.external.LeverJobParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke live optionnel : GET Lever demo uniquement, ecriture sur hirelink_ext_phase02.
 * Lancer : mvn -Dtest=Phase2LiveAtsImportSmokeTest -Dhirelink.phase2.live=true test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/hirelink_ext_phase02?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "hirelink.seed.enabled=false",
        "hirelink.scheduling.enabled=false",
        "hirelink.external-jobs.startup-sync=false",
        "hirelink.external-jobs.maroc.enabled=false",
        "hirelink.external-jobs.remoteok.enabled=false",
        "hirelink.external-jobs.greenhouse.enabled=false",
        "hirelink.external-jobs.lever.enabled=false",
        "hirelink.external-jobs.ashby.enabled=false",
        "hirelink.jwt.secret=hirelink-phase2-live-smoke-key-not-for-real-use-000000",
        "hirelink.security.bcrypt-strength=4"
})
@EnabledIfSystemProperty(named = "hirelink.phase2.live", matches = "true")
class Phase2LiveAtsImportSmokeTest {

    @Autowired ExternalJobImportService imports;
    @Autowired ExternalJobOfferRepository offers;
    @Autowired Environment env;

    @Test
    void importLeverDemoSurSchemaIsole() {
        String url = env.getProperty("spring.datasource.url", "");
        assertTrue(url.contains("hirelink_ext_phase02"));
        assertFalse(url.contains("/hirelink?"));

        AtsHttpClient http = new AtsHttpClient(8000, 20000, 2_000_000, 500,
                "HireLink/1.0 (phase2-live-smoke; isolated; +https://hirelink.ma)",
                "api.lever.co", true);
        String body = http.getJson("https://api.lever.co/v0/postings/leverdemo?mode=json&limit=5");
        ExternalBoardDefinition board = new ExternalBoardDefinition(
                "LEVER", "leverdemo", "Lever Demo",
                "https://jobs.lever.co/leverdemo", "DEMO", true,
                "Lever Postings API demo documentee",
                "Live smoke 2026-11-13");
        List<ParsedExternalJob> jobs = new LeverJobParser().parse(body, board);
        assertFalse(jobs.isEmpty());
        long before = offers.findBySource("LEVER").size();
        ExternalJobImportService.SyncReport report = imports.importSnapshot("LEVER", jobs);
        assertTrue(report.created() + report.updated() >= 1);
        long after = offers.findBySource("LEVER").size();
        assertTrue(after >= before);
        assertTrue(offers.findBySource("LEVER").stream()
                .anyMatch(o -> o.getOriginUrl() != null && o.getOriginUrl().startsWith("https://jobs.lever.co/")));
        System.out.println("LIVE_SMOKE created=" + report.created() + " updated=" + report.updated()
                + " quarantined=" + report.quarantined() + " leverRows=" + after);
    }
}