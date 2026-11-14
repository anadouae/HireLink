package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.Application;
import ma.ensat.hirelink.model.ExternalJobOffer;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.ExternalJobOfferRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.ExternalJobImportService;
import ma.ensat.hirelink.service.MarocPartnerFeedParser;
import ma.ensat.hirelink.service.ParsedExternalJob;
import ma.ensat.hirelink.service.SessionService;
import ma.ensat.hirelink.service.UrlQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 - qualite URL / quarantaine sur hirelink_test uniquement (create-drop).
 * Aucune ecriture sur la base de demonstration hirelink.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExternalUrlQualityIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired ExternalJobOfferRepository externalOffers;
    @Autowired ExternalJobImportService imports;
    @Autowired JobOfferRepository jobOffers;
    @Autowired ApplicationRepository applications;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void cleanExternal() {
        externalOffers.deleteAll();
    }

    @Test
    void valideDeepSansInventerApplyEtCtaFiable() {
        ParsedExternalJob job = new ParsedExternalJob(
                "deep-1", "Java", "Acme",
                "https://jobs.lever.co/acme/abcdef12-3456-7890-abcd-ef1234567890",
                null, "Casablanca", "CDI", "Desc", "Java", null, null, Instant.now());
        ExternalJobImportService.SyncReport report = imports.importSnapshot("REMOTE_OK", List.of(job));
        assertEquals(1, report.created());
        assertEquals(0, report.quarantined());
        ExternalJobOffer row = externalOffers.findBySource("REMOTE_OK").get(0);
        assertEquals(UrlQuality.DEEP.name(), row.getUrlQuality());
        assertNull(row.getApplyUrl());
        assertFalse(row.isQuarantined());
        assertTrue(row.isAvailable());

        User candidate = compte(Role.CANDIDAT);
        ResponseEntity<List> list = http.exchange("/api/v1/external-offers", HttpMethod.GET, auth(token(candidate)), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertEquals(1, list.getBody().size());
        Map body = (Map) list.getBody().get(0);
        assertEquals(Boolean.TRUE, body.get("linkReliable"));
        assertEquals(Boolean.TRUE, body.get("directAccessTrusted"));
        assertNull(body.get("applyUrl"));
        assertTrue(String.valueOf(body.get("originUrl")).startsWith("https://"));
    }

    @Test
    void homepageEtMalformeeEtExpireeEnQuarantaineSansSuppression() {
        List<ParsedExternalJob> jobs = List.of(
                new ParsedExternalJob("home-1", "A", "Co", "https://www.rekrute.com/", "https://www.rekrute.com/",
                        "Casa", "CDI", "d", "Java", null, null, Instant.now()),
                new ParsedExternalJob("bad-1", "B", "Co", "http://example.com/jobs/1", null,
                        "Casa", "CDI", "d", "Java", null, null, Instant.now()),
                new ParsedExternalJob("old-1", "C", "Co", "https://boards.greenhouse.io/x/jobs/999001",
                        "https://boards.greenhouse.io/x/jobs/999001",
                        "Casa", "CDI", "d", "Java", null, null, Instant.now().minus(200, ChronoUnit.DAYS))
        );
        ExternalJobImportService.SyncReport report = imports.importSnapshot("REMOTE_OK", jobs);
        assertEquals(3, report.created());
        assertEquals(3, report.quarantined());
        assertEquals(3, externalOffers.count());
        assertEquals(0, externalOffers.findByAvailableTrueOrderByPublishedAtDesc().size());
        assertTrue(externalOffers.findAll().stream().allMatch(ExternalJobOffer::isQuarantined));
    }

    @Test
    void offreExterneSansApplyUrlNeFabriquePasDeLien() {
        imports.importSnapshot("REMOTE_OK", List.of(new ParsedExternalJob(
                "no-apply", "Dev", "Co",
                "https://jobs.ashbyhq.com/acme/12345-job",
                null, null, null, null, null, null, null, Instant.now())));
        ExternalJobOffer row = externalOffers.findBySource("REMOTE_OK").get(0);
        assertNull(row.getApplyUrl());
        assertEquals(row.getOriginUrl(), "https://jobs.ashbyhq.com/acme/12345-job");
    }

    @Test
    void sampleMarocHomepagesQuarantineesHorsVitrine() throws Exception {
        String json = StreamUtils.copyToString(
                new ClassPathResource("feeds/maroc-emploi-partenaires.v1.json").getInputStream(),
                StandardCharsets.UTF_8);
        List<ParsedExternalJob> jobs = new MarocPartnerFeedParser().parse(json);
        ExternalJobImportService.SyncReport report = imports.importSnapshot(MarocPartnerFeedParser.SOURCE, jobs);
        long quarantined = externalOffers.findBySource(MarocPartnerFeedParser.SOURCE).stream()
                .filter(ExternalJobOffer::isQuarantined).count();
        long available = externalOffers.findBySource(MarocPartnerFeedParser.SOURCE).stream()
                .filter(ExternalJobOffer::isAvailable).count();
        assertTrue(report.created() >= 8);
        assertEquals(report.created(), quarantined);
        assertEquals(0, available);
        assertEquals(0, externalOffers.findByAvailableTrueOrderByPublishedAtDesc().stream()
                .filter(o -> MarocPartnerFeedParser.SOURCE.equals(o.getSource())).count());
    }

    @Test
    void nonRegressionOffreInterneMatchingEtCandidature() {
        User recruiter = compte(Role.RECRUTEUR);
        User candidate = compte(Role.CANDIDAT);
        candidate.setSkills("Java, Spring");
        users.save(candidate);
        JobOffer draft = new JobOffer();
        draft.setRecruiter(recruiter);
        draft.setTitle("Backend Java");
        draft.setDescription("Description assez longue pour une offre interne de non-regression.");
        draft.setRequiredSkills("Java, Spring");
        draft.setPublished(true);
        final JobOffer offer = jobOffers.save(draft);

        imports.importSnapshot("REMOTE_OK", List.of(new ParsedExternalJob(
                "ext-noise", "Noise", "ExtCo", "https://www.rekrute.com/", null,
                null, null, null, "Java", null, null, Instant.now())));

        long appsBefore = applications.count();
        ResponseEntity<Map> apply = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of(
                        "jobOfferId", offer.getId(),
                        "cvSource", "UPLOAD",
                        "cvFileName", "cv-nonreg.txt")), Map.class);
        assertEquals(HttpStatus.OK, apply.getStatusCode());
        assertEquals(appsBefore + 1, applications.count());
        final Long offerId = offer.getId();
        Application saved = applications.findAll().stream()
                .filter(a -> a.getJobOffer().getId().equals(offerId))
                .findFirst().orElseThrow();
        assertTrue(saved.getMatchScore() == null || saved.getMatchScore() >= 0);

        ResponseEntity<String> disguised = http.exchange("/api/v1/applications", HttpMethod.POST,
                json(token(candidate), Map.of("jobOfferId", "ext-" + externalOffers.findAll().get(0).getId())), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, disguised.getStatusCode());
        assertTrue(jobOffers.findById(offerId).isPresent());
        assertEquals("Backend Java", jobOffers.findById(offerId).orElseThrow().getTitle());
    }

    private User compte(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-uq-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        user.setFullName("Recette URL");
        user.setFirstName("Recette");
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        return users.save(user);
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Object> json(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
