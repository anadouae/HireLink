package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.OrganizationService;
import ma.ensat.hirelink.service.SessionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BillingPaymentIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired OrganizationService organizations;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;

    private String token(User user) {
        return jwt.generate(user, sessions.create(user, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
    }

    private User compte(Role role, String nom) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        user.setFullName(nom);
        user.setFirstName(nom);
        user.setRole(role);
        user.setPlanTier(role == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES);
        user.setEmailVerified(true);
        user.setProfileComplete(true);
        user.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        return users.save(user);
    }

    private HttpEntity<Object> json(String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    @Test
    void paiementSimuleActiveLaFormuleEtRefuseLesDoublons() throws Exception {
        User owner = compte(Role.RECRUTEUR, "Nadia Test");
        organizations.createOwned(owner, "Atlas Test " + owner.getId(), "001122334455667");
        String ownerToken = token(users.findById(owner.getId()).orElseThrow());
        User stranger = compte(Role.RECRUTEUR, "Autre Entreprise");
        organizations.createOwned(stranger, "Autre " + stranger.getId(), null);
        String strangerToken = token(stranger);

        ResponseEntity<Map> prepared = http.exchange("/api/v1/billing/subscribe", HttpMethod.POST, json(ownerToken, Map.of("cycle", "month")), Map.class);
        assertEquals(HttpStatus.OK, prepared.getStatusCode(), () -> String.valueOf(prepared.getBody()));
        assertEquals("ACCES", prepared.getBody().get("planTier"));
        @SuppressWarnings("unchecked")
        Map<String, Object> invoice = (Map<String, Object>) prepared.getBody().get("invoice");
        assertEquals("NON_PAYEE", invoice.get("status"));
        assertEquals(299, ((Number) invoice.get("amountTtc")).intValue());
        assertEquals(Boolean.FALSE, invoice.get("vatConfigured"));
        Number invoiceId = (Number) invoice.get("id");

        ResponseEntity<Map> failed = http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(ownerToken, Map.of("invoiceId", invoiceId, "scenario", "FAILURE", "idempotencyKey", "echec-12345678")), Map.class);
        assertEquals(HttpStatus.OK, failed.getStatusCode());
        assertEquals("FAILED", failed.getBody().get("status"));
        assertEquals("NON_PAYEE", ((Map<?, ?>) failed.getBody().get("invoice")).get("status"));

        ResponseEntity<Map> tampered = http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(ownerToken, Map.of("invoiceId", invoiceId, "scenario", "SUCCESS", "idempotencyKey", "montant-123456", "amountTtc", 1)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, tampered.getStatusCode());

        ResponseEntity<Map> paid = http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(ownerToken, Map.of("invoiceId", invoiceId, "scenario", "SUCCESS", "idempotencyKey", "succes-123456")), Map.class);
        assertEquals(HttpStatus.OK, paid.getStatusCode(), () -> String.valueOf(paid.getBody()));
        assertEquals("SUCCEEDED", paid.getBody().get("status"));
        assertEquals("PREMIUM", paid.getBody().get("planTier"));
        assertEquals("PAYEE", ((Map<?, ?>) paid.getBody().get("invoice")).get("status"));
        String reference = String.valueOf(paid.getBody().get("reference"));
        assertTrue(reference.startsWith("SIM-"));

        ResponseEntity<Map> replay = http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(ownerToken, Map.of("invoiceId", invoiceId, "scenario", "SUCCESS", "idempotencyKey", "succes-123456")), Map.class);
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals(reference, replay.getBody().get("reference"));

        ResponseEntity<Map> again = http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(ownerToken, Map.of("invoiceId", invoiceId, "scenario", "SUCCESS", "idempotencyKey", "second-123456")), Map.class);
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());

        ResponseEntity<byte[]> pdf = http.exchange("/api/v1/billing/invoices/" + invoiceId + "/pdf", HttpMethod.GET, auth(ownerToken), byte[].class);
        assertEquals(HttpStatus.OK, pdf.getStatusCode());
        assertEquals("%PDF", new String(pdf.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (var doc = Loader.loadPDF(pdf.getBody())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Facture"));
            assertTrue(text.contains(reference));
            assertTrue(text.contains("Payée") || text.contains("Payee"));
        }

        ResponseEntity<byte[]> denied = http.exchange("/api/v1/billing/invoices/" + invoiceId + "/pdf", HttpMethod.GET, auth(strangerToken), byte[].class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());

        ResponseEntity<String> strangerList = http.exchange("/api/v1/billing/invoices?scope=company", HttpMethod.GET, auth(strangerToken), String.class);
        assertEquals(HttpStatus.OK, strangerList.getStatusCode());
        assertFalse(strangerList.getBody().contains(String.valueOf(invoice.get("number"))));

        ResponseEntity<Map> cancel = http.exchange("/api/v1/billing/subscription", HttpMethod.DELETE, auth(ownerToken), Map.class);
        assertEquals(HttpStatus.OK, cancel.getStatusCode(), () -> String.valueOf(cancel.getBody()));
        assertEquals("PREMIUM", cancel.getBody().get("planTier"));
        assertEquals(Boolean.TRUE, cancel.getBody().get("cancelAtPeriodEnd"));

        ResponseEntity<Map> card = http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(ownerToken, Map.of("invoiceId", invoiceId, "scenario", "SUCCESS", "idempotencyKey", "carte-12345678", "pan", "4242424242424242")), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, card.getStatusCode());
    }

    @Test
    void candidatNeVoitPasLaFactureDuneEntreprise() {
        User owner = compte(Role.RECRUTEUR, "Salma Test");
        organizations.createOwned(owner, "Studio " + owner.getId(), null);
        String ownerToken = token(users.findById(owner.getId()).orElseThrow());
        ResponseEntity<Map> prepared = http.exchange("/api/v1/billing/subscribe", HttpMethod.POST, json(ownerToken, Map.of("cycle", "month")), Map.class);
        String number = String.valueOf(((Map<?, ?>) prepared.getBody().get("invoice")).get("number"));
        User candidate = compte(Role.CANDIDAT, "Sara Test");
        ResponseEntity<String> mine = http.exchange("/api/v1/billing/invoices?scope=mine", HttpMethod.GET, auth(token(candidate)), String.class);
        assertEquals(HttpStatus.OK, mine.getStatusCode());
        assertFalse(mine.getBody().contains(number));
    }
}
