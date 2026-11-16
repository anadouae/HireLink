package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vague 6 — isolation entre deux candidats distincts (IDOR profil / CV / me).
 * Base : hirelink_test uniquement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CandidateIsolationIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;

    private record Cand(Long id, String token) {}

    private Cand candidate(String label) {
        String seed = Long.toHexString(System.nanoTime()) + label.hashCode();
        User u = new User();
        u.setEmail("cand-" + seed + "@test.hirelink.local");
        u.setFullName("Candidat " + label);
        u.setRole(Role.CANDIDAT);
        u.setPlanTier(PlanTier.GRATUIT);
        u.setEmailVerified(true);
        u.setProfileComplete(true);
        u.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        users.save(u);
        String token = jwt.generate(u, sessions.create(u, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
        return new Cand(u.getId(), token);
    }

    private HttpEntity<Void> auth(Cand c) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(c.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(h);
    }

    @Test
    void twoCandidatesHaveDistinctMeProfiles() {
        Cand a = candidate("A");
        Cand b = candidate("B");
        assertNotEquals(a.id(), b.id());

        ResponseEntity<Map> meA = http.exchange("/api/v1/me", HttpMethod.GET, auth(a), Map.class);
        ResponseEntity<Map> meB = http.exchange("/api/v1/me", HttpMethod.GET, auth(b), Map.class);
        assertEquals(HttpStatus.OK, meA.getStatusCode());
        assertEquals(HttpStatus.OK, meB.getStatusCode());
        assertEquals(a.id().intValue(), ((Number) meA.getBody().get("id")).intValue());
        assertEquals(b.id().intValue(), ((Number) meB.getBody().get("id")).intValue());
        assertNotEquals(meA.getBody().get("email"), meB.getBody().get("email"));
    }

    @Test
    void candidateCannotDownloadForeignApplicationCv() {
        Cand a = candidate("Intrus");
        // ID inexistant / étranger : 404/403 — jamais un CV d'autrui.
        ResponseEntity<String> res = http.exchange(
                "/api/v1/applications/999999991/cv",
                HttpMethod.GET,
                auth(a),
                String.class);
        assertTrue(
                res.getStatusCode() == HttpStatus.NOT_FOUND
                        || res.getStatusCode() == HttpStatus.FORBIDDEN,
                "corps=" + res.getBody() + " status=" + res.getStatusCode());
    }
}
