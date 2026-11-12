package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.AuditLog;
import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.AuditLogRepository;
import ma.ensat.hirelink.repository.BillingInvoiceRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.OrganizationService;
import ma.ensat.hirelink.service.SessionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Couvre les parcours absents des 109 tests d'origine : profil, photo, mot de passe,
 * fin de p\u00E9riode, CSV, refus, e-mails journalis\u00E9s et mise en page PDF.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinalisationCoverageIntegrationTest {

    private static final String MOT_DE_PASSE = "Phrase-de-validation-2026";
    private static final String MOT_DE_PASSE_SUIVANT = "Autre-phrase-valide-2026";

    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organisations;
    @Autowired OrganizationService organizations;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired AuditLogRepository auditLogs;
    @Autowired BillingInvoiceRepository invoices;

    @Test
    void profilPhotoEtMotDePasseSontPersistants() throws Exception {
        User admin = compte(Role.ADMINISTRATEUR, "Youssef Admin");
        admin.setPasswordHash(encoder.encode(MOT_DE_PASSE));
        users.save(admin);
        String courant = token(admin);
        String autreSession = token(users.findById(admin.getId()).orElseThrow());

        ResponseEntity<Map> profil = http.exchange("/api/v1/account/profile", HttpMethod.PUT,
                json(courant, Map.of("firstName", "\u00C9lodie", "lastName", "\u0641\u0627\u0637\u0645\u0629", "email", admin.getEmail())), Map.class);
        assertEquals(HttpStatus.OK, profil.getStatusCode(), () -> String.valueOf(profil.getBody()));
        assertEquals("\u00C9lodie", profil.getBody().get("firstName"));
        assertEquals("\u0641\u0627\u0637\u0645\u0629", profil.getBody().get("lastName"));
        User relu = users.findById(admin.getId()).orElseThrow();
        assertEquals("\u00C9lodie", relu.getFirstName());
        assertEquals("\u0641\u0627\u0637\u0645\u0629", relu.getLastName());
        assertEquals("\u00C9lodie \u0641\u0627\u0637\u0645\u0629", relu.getFullName());

        byte[] premiere = png(Color.RED);
        byte[] seconde = png(Color.BLUE);
        assertEquals(HttpStatus.OK, envoyerPhoto(courant, premiere).getStatusCode());
        assertEquals(HttpStatus.OK, envoyerPhoto(courant, seconde).getStatusCode());
        ResponseEntity<byte[]> photo = http.exchange("/api/v1/account/avatar", HttpMethod.GET, auth(courant), byte[].class);
        assertEquals(HttpStatus.OK, photo.getStatusCode());
        assertEquals(seconde.length, photo.getBody().length);
        assertEquals(HttpStatus.OK, http.exchange("/api/v1/account/avatar", HttpMethod.DELETE, auth(courant), String.class).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, http.exchange("/api/v1/account/avatar", HttpMethod.GET, auth(courant), String.class).getStatusCode());

        ResponseEntity<String> changement = http.exchange("/api/v1/account/password", HttpMethod.POST,
                json(courant, Map.of("currentPassword", MOT_DE_PASSE, "newPassword", MOT_DE_PASSE_SUIVANT, "confirmPassword", MOT_DE_PASSE_SUIVANT)), String.class);
        assertEquals(HttpStatus.OK, changement.getStatusCode(), changement::getBody);
        assertEquals(HttpStatus.FORBIDDEN, http.exchange("/api/v1/account/password", HttpMethod.POST,
                json(courant, Map.of("currentPassword", "mauvais-mot-de-passe", "newPassword", MOT_DE_PASSE_SUIVANT, "confirmPassword", MOT_DE_PASSE_SUIVANT)), String.class).getStatusCode());
        assertNotEquals(HttpStatus.OK, http.exchange("/api/v1/account/profile", HttpMethod.GET, auth(autreSession), String.class).getStatusCode());

        String reconnexion = connexion(admin.getEmail(), MOT_DE_PASSE_SUIVANT);
        assertFalse(reconnexion.isBlank());
        assertEquals(HttpStatus.OK, http.exchange("/api/v1/account/profile", HttpMethod.GET, auth(reconnexion), String.class).getStatusCode());
        assertTrue(connexionEchoue(admin.getEmail(), MOT_DE_PASSE));
    }

    @Test
    void laPeriodeEchueRendLaFormuleGratuite() {
        User owner = compte(Role.RECRUTEUR, "Nadia Periode");
        Organization org = organizations.createOwned(owner, "Atlas Periode " + owner.getId(), null);
        String ownerToken = token(users.findById(owner.getId()).orElseThrow());
        Number invoiceId = souscrire(ownerToken);
        payer(ownerToken, invoiceId, "periode-succes");

        Organization fraiche = organisations.findById(org.getId()).orElseThrow();
        fraiche.setSubscriptionEndsAt(Instant.now().minusSeconds(120));
        fraiche.setCancelAtPeriodEnd(false);
        organisations.saveAndFlush(fraiche);
        ResponseEntity<Map> encore = http.exchange("/api/v1/billing/subscribe", HttpMethod.POST, json(ownerToken, Map.of("cycle", "year")), Map.class);
        assertEquals("PREMIUM", encore.getBody().get("planTier"));

        fraiche = organisations.findById(org.getId()).orElseThrow();
        fraiche.setSubscriptionEndsAt(Instant.now().minusSeconds(120));
        fraiche.setCancelAtPeriodEnd(true);
        organisations.saveAndFlush(fraiche);
        ResponseEntity<Map> terminee = http.exchange("/api/v1/billing/subscribe", HttpMethod.POST, json(ownerToken, Map.of("cycle", "year")), Map.class);
        assertEquals(HttpStatus.OK, terminee.getStatusCode(), () -> String.valueOf(terminee.getBody()));
        assertEquals("ACCES", terminee.getBody().get("planTier"));
        assertEquals(PlanTier.ACCES, organisations.findById(org.getId()).orElseThrow().getPlanTier());

        User candidate = compte(Role.CANDIDAT, "Sara Periode");
        candidate.setPlanTier(PlanTier.PERFORMANCE);
        candidate.setCancelAtPeriodEnd(true);
        candidate.setSubscriptionEndsAt(Instant.now().minusSeconds(120));
        users.saveAndFlush(candidate);
        ResponseEntity<Map> candidat = http.exchange("/api/v1/billing/subscribe", HttpMethod.POST, json(token(candidate), Map.of("cycle", "month")), Map.class);
        assertEquals("ESSENTIEL", candidat.getBody().get("planTier"));
        assertEquals(PlanTier.ESSENTIEL, users.findById(candidate.getId()).orElseThrow().getPlanTier());
    }

    @Test
    void exportCsvFiltreProtegeEtRefuseLesAutresRoles() {
        User admin = compte(Role.ADMINISTRATEUR, "Auditeur");
        User recruiter = compte(Role.RECRUTEUR, "Recruteur Export");
        String marqueur = "csv-" + Long.toHexString(System.nanoTime());
        auditLogs.save(ligne("CSV_" + marqueur, "=1+1;\u0641\u0627\u0637\u0645\u0629;d\u00E9j\u00E0"));
        auditLogs.save(ligne("AUTRE_" + marqueur, "ligne-a-exclure"));

        ResponseEntity<String> interdit = http.exchange("/api/v1/admin/audit/export", HttpMethod.GET, auth(token(recruiter)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, interdit.getStatusCode());

        ResponseEntity<byte[]> csv = http.exchange("/api/v1/admin/audit/export?action=CSV_" + marqueur, HttpMethod.GET, auth(token(admin)), byte[].class);
        assertEquals(HttpStatus.OK, csv.getStatusCode());
        assertEquals((byte) 0xEF, csv.getBody()[0]);
        assertEquals((byte) 0xBB, csv.getBody()[1]);
        assertEquals((byte) 0xBF, csv.getBody()[2]);
        String texte = new String(csv.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(texte.contains("Date;Action;Compte;Cible;IP;R\u00E9sultat;D\u00E9tail"));
        assertTrue(texte.contains("\u0641\u0627\u0637\u0645\u0629"));
        assertTrue(texte.contains("d\u00E9j\u00E0"));
        assertTrue(texte.contains("'=1+1"));
        assertFalse(texte.contains("ligne-a-exclure"));
        assertTrue(texte.startsWith("\uFEFF"));
    }

    @Test
    void facturePdfGardeLaPageA4EtNinventePasLaFiscalite() throws Exception {
        User candidate = compte(Role.CANDIDAT, "\u00C9lodie \u0641\u0627\u0637\u0645\u0629");
        String candidateToken = token(candidate);
        ResponseEntity<Map> prepared = http.exchange("/api/v1/billing/subscribe", HttpMethod.POST, json(candidateToken, Map.of("cycle", "month")), Map.class);
        Number id = (Number) ((Map<?, ?>) prepared.getBody().get("invoice")).get("id");
        ResponseEntity<byte[]> pdf = http.exchange("/api/v1/billing/invoices/" + id + "/pdf", HttpMethod.GET, auth(candidateToken), byte[].class);
        assertEquals(HttpStatus.OK, pdf.getStatusCode());
        try (var doc = Loader.loadPDF(pdf.getBody())) {
            assertEquals(1, doc.getNumberOfPages());
            PDRectangle page = doc.getPage(0).getMediaBox();
            assertEquals(PDRectangle.A4.getWidth(), page.getWidth(), 0.5);
            assertEquals(PDRectangle.A4.getHeight(), page.getHeight(), 0.5);
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Facture"));
            assertTrue(text.contains("Adresse et ICE"));
            assertTrue(text.contains("non renseign"));
            assertTrue(text.contains("TVA"));
            assertTrue(text.contains("non configur"));
            assertTrue(text.contains("\u00C9lodie") || text.contains("Elodie"));
            assertTrue(text.contains("\u0641") && text.contains("\u0629"), text);
            assertFalse(text.contains("ICE 00"));
        }
    }

    @Test
    void doubleClicEtRelectureRetrouventLaMemeTransaction() throws Exception {
        User owner = compte(Role.RECRUTEUR, "Nadia Double");
        organizations.createOwned(owner, "Atlas Double " + owner.getId(), null);
        String ownerToken = token(users.findById(owner.getId()).orElseThrow());
        Number invoiceId = souscrire(ownerToken);
        String cle = "double-" + Long.toHexString(System.nanoTime());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> un = pool.submit(() -> payerBrut(ownerToken, invoiceId, cle));
            Future<ResponseEntity<Map>> deux = pool.submit(() -> payerBrut(ownerToken, invoiceId, cle));
            ResponseEntity<Map> a = un.get();
            ResponseEntity<Map> b = deux.get();
            assertEquals(HttpStatus.OK, a.getStatusCode(), () -> String.valueOf(a.getBody()));
            assertEquals(HttpStatus.OK, b.getStatusCode(), () -> String.valueOf(b.getBody()));
            assertEquals(a.getBody().get("reference"), b.getBody().get("reference"));
        } finally {
            pool.shutdownNow();
        }
        ResponseEntity<Map> relu = http.exchange("/api/v1/billing/payments?idempotencyKey=" + cle, HttpMethod.GET, auth(ownerToken), Map.class);
        assertEquals(HttpStatus.OK, relu.getStatusCode());
        assertEquals("SUCCEEDED", relu.getBody().get("status"));

        BillingInvoice ouverte = invoices.findById(souscrire(ownerToken).longValue()).orElseThrow();
        ouverte.setStatus("ANNULEE");
        invoices.saveAndFlush(ouverte);
        ResponseEntity<String> annulee = http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(ownerToken, Map.of("invoiceId", ouverte.getId(), "scenario", "SUCCESS", "idempotencyKey", "annulee-" + cle)), String.class);
        assertEquals(HttpStatus.CONFLICT, annulee.getStatusCode());
    }

    @Test
    void refusAvecMotifEtEmailJournalise() {
        User owner = compte(Role.RECRUTEUR, "Nadia Refus");
        Organization org = organizations.createOwned(owner, "Atlas Refus " + owner.getId(), "001122334455667");
        org.setVerified(true);
        org.setVerificationStatus("ADMIN_APPROVED");
        organisations.saveAndFlush(org);
        owner.setCompanyVerified(true);
        users.saveAndFlush(owner);
        String ownerToken = token(users.findById(owner.getId()).orElseThrow());
        ResponseEntity<Map> offre = http.exchange("/api/v1/offers", HttpMethod.POST, json(ownerToken, Map.of(
                "title", "Analyste " + owner.getId(),
                "description", "Conception et maintien d'applications web. Poste bas\u00E9 \u00E0 T\u00E9touan, en hybride.",
                "contractType", "CDI",
                "published", true)), Map.class);
        assertEquals(HttpStatus.OK, offre.getStatusCode(), () -> String.valueOf(offre.getBody()));
        User candidate = compte(Role.CANDIDAT, "Sara Refus");
        String candidateToken = token(candidate);
        ResponseEntity<Map> candidature = http.exchange("/api/v1/applications", HttpMethod.POST, json(candidateToken, Map.of(
                "jobOfferId", offre.getBody().get("id"),
                "cvSource", "UPLOAD",
                "cvFileName", "cv-recette.txt")), Map.class);
        assertEquals(HttpStatus.OK, candidature.getStatusCode(), () -> String.valueOf(candidature.getBody()));
        ResponseEntity<Map> refus = http.exchange("/api/v1/applications/" + candidature.getBody().get("id") + "/status", HttpMethod.PATCH,
                json(ownerToken, Map.of(
                        "status", "REJECTED",
                        "rejectionReason", "REF06:pr\u00E9cision \u0641\u0627\u0637\u0645\u0629",
                        "confirmIdentity", true,
                        "confirmCvReviewed", true,
                        "confirmSlaAware", true)), Map.class);
        assertEquals(HttpStatus.OK, refus.getStatusCode(), () -> String.valueOf(refus.getBody()));
        assertEquals("REF06", refus.getBody().get("refusalReasonCode"));
        assertEquals("pr\u00E9cision \u0641\u0627\u0637\u0645\u0629", refus.getBody().get("rejectionReason"));

        User admin = compte(Role.ADMINISTRATEUR, "Admin Courrier");
        ResponseEntity<String> emails = http.exchange("/api/v1/admin/emails", HttpMethod.GET, auth(token(admin)), String.class);
        assertEquals(HttpStatus.OK, emails.getStatusCode());
        assertTrue(emails.getBody().contains("LOGGED"));
        assertFalse(emails.getBody().contains("\"SENT\""));
        ResponseEntity<String> journal = http.exchange("/api/v1/admin/audit?page=0&size=50", HttpMethod.GET, auth(token(admin)), String.class);
        assertEquals(HttpStatus.OK, journal.getStatusCode());
        assertTrue(journal.getBody().contains("APPLICATION_REFUSED"));
    }

    @Test
    void chaqueMotifCatalogueEstEnregistreNotifieEtHistorise() {
        User owner = compte(Role.RECRUTEUR, "Nadia Catalogue");
        Organization org = organizations.createOwned(owner, "Atlas Catalogue " + owner.getId(), "001122334455668");
        org.setVerified(true);
        org.setVerificationStatus("ADMIN_APPROVED");
        organisations.saveAndFlush(org);
        owner.setCompanyVerified(true);
        users.saveAndFlush(owner);
        String ownerToken = token(users.findById(owner.getId()).orElseThrow());
        ResponseEntity<Map> offre = http.exchange("/api/v1/offers", HttpMethod.POST, json(ownerToken, Map.of(
                "title", "Catalogue " + owner.getId(),
                "description", "Conception et maintien d'applications web. Poste basé à Tétouan, en hybride.",
                "contractType", "CDI",
                "published", true)), Map.class);
        assertEquals(HttpStatus.OK, offre.getStatusCode(), () -> String.valueOf(offre.getBody()));
        Number offerId = (Number) offre.getBody().get("id");
        boolean casesVerifiees = false;

        for (String code : new String[]{"REF01", "REF02", "REF03", "REF04", "REF05"}) {
            User candidate = compte(Role.CANDIDAT, "Candidat " + code);
            String candidateToken = token(candidate);
            ResponseEntity<Map> candidature = http.exchange("/api/v1/applications", HttpMethod.POST, json(candidateToken, Map.of(
                    "jobOfferId", offerId, "cvSource", "UPLOAD", "cvFileName", "cv-" + code + ".txt")), Map.class);
            assertEquals(HttpStatus.OK, candidature.getStatusCode(), () -> code + " " + candidature.getBody());
            Number appId = (Number) candidature.getBody().get("id");

            ResponseEntity<String> interdit = http.exchange("/api/v1/applications/" + appId + "/status", HttpMethod.PATCH,
                    json(candidateToken, Map.of(
                            "status", "REJECTED", "rejectionReason", code,
                            "confirmIdentity", true, "confirmCvReviewed", true, "confirmSlaAware", true)), String.class);
            assertEquals(HttpStatus.FORBIDDEN, interdit.getStatusCode(), code);

            if (!casesVerifiees) {
                ResponseEntity<String> sansCases = http.exchange("/api/v1/applications/" + appId + "/status", HttpMethod.PATCH,
                        json(ownerToken, Map.of("status", "REJECTED", "rejectionReason", code)), String.class);
                assertEquals(HttpStatus.BAD_REQUEST, sansCases.getStatusCode(), code);
                casesVerifiees = true;
            }

            ResponseEntity<Map> refus = http.exchange("/api/v1/applications/" + appId + "/status", HttpMethod.PATCH,
                    json(ownerToken, Map.of(
                            "status", "REJECTED", "rejectionReason", code,
                            "confirmIdentity", true, "confirmCvReviewed", true, "confirmSlaAware", true)), Map.class);
            assertEquals(HttpStatus.OK, refus.getStatusCode(), () -> code + " " + refus.getBody());
            assertEquals("REJECTED", refus.getBody().get("status"));
            assertEquals(code, refus.getBody().get("refusalReasonCode"));

            ResponseEntity<String> historique = http.exchange("/api/v1/applications/" + appId + "/timeline",
                    HttpMethod.GET, auth(ownerToken), String.class);
            assertEquals(HttpStatus.OK, historique.getStatusCode(), code);
            assertTrue(historique.getBody().contains("REJECTED"), code);

            ResponseEntity<String> cloche = http.exchange("/api/v1/notifications", HttpMethod.GET, auth(candidateToken), String.class);
            assertEquals(HttpStatus.OK, cloche.getStatusCode(), code);
            assertTrue(cloche.getBody().contains("Candidature non retenue"), code);
        }

        User autre = compte(Role.CANDIDAT, "Candidat Autre");
        String autreToken = token(autre);
        ResponseEntity<Map> dossier = http.exchange("/api/v1/applications", HttpMethod.POST, json(autreToken, Map.of(
                "jobOfferId", offerId, "cvSource", "UPLOAD", "cvFileName", "cv-autre.txt")), Map.class);
        assertEquals(HttpStatus.OK, dossier.getStatusCode(), () -> String.valueOf(dossier.getBody()));
        Number autreId = (Number) dossier.getBody().get("id");
        ResponseEntity<String> nu = http.exchange("/api/v1/applications/" + autreId + "/status", HttpMethod.PATCH,
                json(ownerToken, Map.of(
                        "status", "REJECTED", "rejectionReason", "REF06",
                        "confirmIdentity", true, "confirmCvReviewed", true, "confirmSlaAware", true)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, nu.getStatusCode());
        ResponseEntity<Map> precis = http.exchange("/api/v1/applications/" + autreId + "/status", HttpMethod.PATCH,
                json(ownerToken, Map.of(
                        "status", "REJECTED", "rejectionReason", "REF06:motif libre de recette",
                        "confirmIdentity", true, "confirmCvReviewed", true, "confirmSlaAware", true)), Map.class);
        assertEquals(HttpStatus.OK, precis.getStatusCode(), () -> String.valueOf(precis.getBody()));
        assertEquals("REF06", precis.getBody().get("refusalReasonCode"));
        assertEquals("motif libre de recette", precis.getBody().get("rejectionReason"));
    }

    private Number souscrire(String token) {
        ResponseEntity<Map> prepared = http.exchange("/api/v1/billing/subscribe", HttpMethod.POST, json(token, Map.of("cycle", "month")), Map.class);
        assertEquals(HttpStatus.OK, prepared.getStatusCode(), () -> String.valueOf(prepared.getBody()));
        return (Number) ((Map<?, ?>) prepared.getBody().get("invoice")).get("id");
    }

    private void payer(String token, Number invoiceId, String cle) {
        ResponseEntity<Map> paid = payerBrut(token, invoiceId, cle);
        assertEquals(HttpStatus.OK, paid.getStatusCode(), () -> String.valueOf(paid.getBody()));
        assertEquals("SUCCEEDED", paid.getBody().get("status"));
    }

    private ResponseEntity<Map> payerBrut(String token, Number invoiceId, String cle) {
        return http.exchange("/api/v1/billing/payments", HttpMethod.POST,
                json(token, Map.of("invoiceId", invoiceId, "scenario", "SUCCESS", "idempotencyKey", cle)), Map.class);
    }

    private ResponseEntity<String> envoyerPhoto(String token, byte[] png) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpHeaders fichier = new HttpHeaders();
        fichier.setContentType(MediaType.IMAGE_PNG);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(png) {
            @Override public String getFilename() { return "photo.png"; }
        }, fichier));
        return http.exchange("/api/v1/account/avatar", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static byte[] png(Color couleur) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) image.setRGB(x, y, couleur.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private AuditLog ligne(String action, String detail) {
        AuditLog row = new AuditLog();
        row.setAction(action);
        row.setActorEmail("audit@test.hirelink.local");
        row.setActorRole("ADMINISTRATEUR");
        row.setTargetType("PROBE");
        row.setTargetId("1");
        row.setSuccess(true);
        row.setDetail(detail);
        return row;
    }

    private String connexion(String email, String password) {
        for (int i = 0; i < 4; i++) {
            Map<String, String> captcha = captcha();
            ResponseEntity<Map> res = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", email, "password", password, "captchaToken", captcha.get("token"), "captchaAnswer", captcha.get("answer")), jsonHeaders()), Map.class);
            if (res.getStatusCode() == HttpStatus.OK && res.getBody() != null && res.getBody().get("accessToken") != null) {
                return String.valueOf(res.getBody().get("accessToken"));
            }
        }
        return "";
    }

    private boolean connexionEchoue(String email, String password) {
        for (int i = 0; i < 4; i++) {
            Map<String, String> captcha = captcha();
            ResponseEntity<String> res = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", email, "password", password, "captchaToken", captcha.get("token"), "captchaAnswer", captcha.get("answer")), jsonHeaders()), String.class);
            if (res.getStatusCode().value() != 428) return res.getStatusCode() != HttpStatus.OK;
        }
        return false;
    }

    private Map<String, String> captcha() {
        ResponseEntity<Map> res = http.exchange("/api/v1/auth/captcha", HttpMethod.POST, new HttpEntity<>(Map.of(), jsonHeaders()), Map.class);
        String kind = String.valueOf(res.getBody().get("kind"));
        String answer;
        if ("question".equals(kind)) {
            String question = String.valueOf(res.getBody().get("question"));
            Matcher nombres = Pattern.compile("(\\d+)").matcher(question);
            int a = nombres.find() ? Integer.parseInt(nombres.group()) : 0;
            int b = nombres.find() ? Integer.parseInt(nombres.group()) : 0;
            answer = String.valueOf(question.contains("+") ? a + b : a - b);
        } else {
            Matcher glyphes = Pattern.compile("x=\"(\\d+)\"[^>]*>([A-Z0-9])<").matcher(String.valueOf(res.getBody().get("svg")));
            StringBuilder mot = new StringBuilder();
            java.util.List<int[]> ordre = new java.util.ArrayList<>();
            while (glyphes.find()) ordre.add(new int[]{Integer.parseInt(glyphes.group(1)), glyphes.group(2).charAt(0)});
            ordre.sort(java.util.Comparator.comparingInt(p -> p[0]));
            for (int[] p : ordre) mot.append((char) p[1]);
            answer = mot.toString();
        }
        return Map.of("token", String.valueOf(res.getBody().get("token")), "answer", answer);
    }

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
        user.setPasswordHash(encoder.encode(MOT_DE_PASSE));
        return users.save(user);
    }

    private HttpEntity<Object> json(String token, Object body) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(body, h);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }
}
