package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.OrganizationService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Gestion par l'administration : désactivation / réactivation / retrait d'entreprise, déconnexion, journaux filtrés. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminManagementIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organisations;
    @Autowired OrganizationService organizations;
    @Autowired JobOfferRepository offers;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    @Test
    void desactiverPuisReactiverRetablitComptesEtOffres() {
        User admin = compte(Role.ADMINISTRATEUR, "Admin Gestion", "test.hirelink.local");
        String adminToken = token(admin);
        User owner = compte(Role.RECRUTEUR, "Nadia Gestion", "atlas-gestion.ma");
        Organization org = organizations.createOwned(owner, "Atlas Gestion " + owner.getId(), null);
        org.setVerified(true);
        org.setVerificationStatus("DOMAIN_EMAIL");
        organisations.saveAndFlush(org);
        owner.setCompanyVerified(true);
        users.saveAndFlush(owner);
        String ownerToken = token(users.findById(owner.getId()).orElseThrow());
        ResponseEntity<Map> offre = http.exchange("/api/v1/offers", HttpMethod.POST, json(ownerToken, Map.of(
                "title", "Gestion " + owner.getId(),
                "description", "Conception et maintien d'applications web. Poste basé à Tétouan, en hybride.",
                "contractType", "CDI",
                "published", true)), Map.class);
        assertEquals(HttpStatus.OK, offre.getStatusCode(), () -> String.valueOf(offre.getBody()));
        long offerId = ((Number) offre.getBody().get("id")).longValue();

        ResponseEntity<Map> off = http.exchange("/api/v1/admin/organizations/" + org.getId() + "/active", HttpMethod.PATCH,
                json(adminToken, Map.of("active", false)), Map.class);
        assertEquals(HttpStatus.OK, off.getStatusCode(), () -> String.valueOf(off.getBody()));
        assertEquals("DISABLED", off.getBody().get("accessStatus"));
        assertFalse(users.findById(owner.getId()).orElseThrow().isActive());
        assertFalse(offers.findById(offerId).orElseThrow().isPublished());
        assertNotEquals(HttpStatus.OK, http.exchange("/api/v1/organizations/mine", HttpMethod.GET, auth(ownerToken), String.class).getStatusCode());

        ResponseEntity<Map> on = http.exchange("/api/v1/admin/organizations/" + org.getId() + "/active", HttpMethod.PATCH,
                json(adminToken, Map.of("active", true)), Map.class);
        assertEquals(HttpStatus.OK, on.getStatusCode());
        assertEquals("ACTIVE", on.getBody().get("accessStatus"));
        assertTrue(users.findById(owner.getId()).orElseThrow().isActive());
        assertTrue(offers.findById(offerId).orElseThrow().isPublished());

        // Validation manuelle refusée, retrait accepté, relance automatique possible.
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange("/api/v1/admin/organizations/" + org.getId(), HttpMethod.PATCH,
                json(adminToken, Map.of("verified", true)), String.class).getStatusCode());
        ResponseEntity<Map> retrait = http.exchange("/api/v1/admin/organizations/" + org.getId(), HttpMethod.PATCH,
                json(adminToken, Map.of("verified", false)), Map.class);
        assertEquals(Boolean.FALSE, retrait.getBody().get("verified"));
        String ownerToken2 = token(users.findById(owner.getId()).orElseThrow());
        assertEquals(HttpStatus.CONFLICT, http.exchange("/api/v1/organizations/mine/verify", HttpMethod.POST, auth(ownerToken2), String.class).getStatusCode());
        ResponseEntity<Map> relance = http.exchange("/api/v1/admin/organizations/" + org.getId() + "/recheck", HttpMethod.POST, auth(adminToken), Map.class);
        assertEquals(HttpStatus.OK, relance.getStatusCode(), () -> String.valueOf(relance.getBody()));
        assertEquals(Boolean.TRUE, relance.getBody().get("verified"));

        ResponseEntity<Map> retire = http.exchange("/api/v1/admin/organizations/" + org.getId(), HttpMethod.DELETE, auth(adminToken), Map.class);
        assertEquals(HttpStatus.OK, retire.getStatusCode());
        Organization relue = organisations.findById(org.getId()).orElseThrow();
        assertTrue(relue.isRemoved());
        assertFalse(relue.isVerified());
        assertEquals(HttpStatus.CONFLICT, http.exchange("/api/v1/admin/organizations/" + org.getId() + "/active", HttpMethod.PATCH,
                json(adminToken, Map.of("active", true)), String.class).getStatusCode());
    }

    @Test
    void deconnecterUnCompteFermeSesSessions() {
        User admin = compte(Role.ADMINISTRATEUR, "Admin Sessions", "test.hirelink.local");
        User cible = compte(Role.CANDIDAT, "Sara Sessions", "test.hirelink.local");
        String cibleToken = token(cible);
        assertEquals(HttpStatus.OK, http.exchange("/api/v1/account/profile", HttpMethod.GET, auth(cibleToken), String.class).getStatusCode());
        ResponseEntity<Map> r = http.exchange("/api/v1/admin/users/" + cible.getId() + "/revoke-sessions", HttpMethod.POST, auth(token(admin)), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(((Number) r.getBody().get("revoked")).intValue() >= 1);
        assertNotEquals(HttpStatus.OK, http.exchange("/api/v1/account/profile", HttpMethod.GET, auth(cibleToken), String.class).getStatusCode());
        assertTrue(users.findById(cible.getId()).orElseThrow().isActive());
    }

    @Test
    void journalEtVueDEnsembleSontFiltresCoteServeur() {
        User admin = compte(Role.ADMINISTRATEUR, "Admin Journal", "test.hirelink.local");
        String t = token(admin);
        String today = LocalDate.now(ZoneId.of("Africa/Casablanca")).toString();
        ResponseEntity<Map> page = http.exchange("/api/v1/admin/audit?size=5&from=" + today + "&to=" + today + "&actions=AUDIT_EXPORTED,ORG_REMOVED",
                HttpMethod.GET, auth(t), Map.class);
        assertEquals(HttpStatus.OK, page.getStatusCode(), () -> String.valueOf(page.getBody()));
        for (Object row : (List<?>) page.getBody().get("rows")) {
            String action = String.valueOf(((Map<?, ?>) row).get("action"));
            assertTrue(action.equals("AUDIT_EXPORTED") || action.equals("ORG_REMOVED"), action);
        }
        assertNotNull(page.getBody().get("total"));
        ResponseEntity<Map> futur = http.exchange("/api/v1/admin/audit?from=2099-01-01", HttpMethod.GET, auth(t), Map.class);
        assertEquals(0, ((Number) futur.getBody().get("total")).intValue());
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange("/api/v1/admin/audit?from=hier", HttpMethod.GET, auth(t), String.class).getStatusCode());

        ResponseEntity<Map> mails = http.exchange("/api/v1/admin/emails?status=FAILED&from=" + today, HttpMethod.GET, auth(t), Map.class);
        assertEquals(HttpStatus.OK, mails.getStatusCode());
        assertNotNull(mails.getBody().get("counts"));

        ResponseEntity<Map> vue = http.exchange("/api/v1/admin/overview?granularity=month", HttpMethod.GET, auth(t), Map.class);
        assertEquals(HttpStatus.OK, vue.getStatusCode(), () -> String.valueOf(vue.getBody()));
        Map<?, ?> revenue = (Map<?, ?>) vue.getBody().get("revenue");
        assertEquals(4, ((List<?>) revenue.get("byPlan")).size());
        Map<?, ?> signups = (Map<?, ?>) vue.getBody().get("signups");
        assertEquals(12, ((List<?>) signups.get("buckets")).size());

        ResponseEntity<String> plans = http.exchange("/api/v1/admin/plans", HttpMethod.GET, auth(t), String.class);
        assertFalse(plans.getBody().contains("\"ACCES\""));
        assertFalse(plans.getBody().contains("\"PREMIUM\""));
        assertFalse(plans.getBody().contains("RETIR"));
    }

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
    }

    private User compte(Role role, String nom, String domaine) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-" + Long.toHexString(System.nanoTime()) + "@" + domaine);
        user.setFullName(nom);
        user.setFirstName(nom);
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.GRATUIT : PlanTier.ESSENTIEL);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash(encoder.encode("Phrase-de-validation-2026"));
        return users.save(user);
    }

    private HttpEntity<Object> json(String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return new HttpEntity<>(body, h);
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }
}
