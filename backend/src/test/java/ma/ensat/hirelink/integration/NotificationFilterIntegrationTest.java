package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.InAppNotification;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.JwtService;
import ma.ensat.hirelink.service.NotificationService;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les filtres de la page Notifications (type, période, lu/non lu, plafond) sont appliqués par la base :
 * cette suite vérifie la chaîne réelle HTTP → garde-fou → requête → réponse, et qu'aucun filtre ne peut
 * faire sortir les notifications d'un autre compte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationFilterIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired UserRepository userRepository;
    @Autowired NotificationService notifications;
    @Autowired SessionService sessionService;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    private record Compte(User user, String token) {}

    /** Le mot de passe n'est jamais utilisé ici : le jeton est émis directement, comme dans les autres tests d'intégration. */
    private Compte compte(String libelle) {
        User u = new User();
        u.setEmail("notif-" + Long.toHexString(System.nanoTime()) + "@test.hirelink.local");
        u.setFullName("Compte " + libelle);
        u.setRole(Role.CANDIDAT);
        u.setPlanTier(PlanTier.ACCES);
        u.setEmailVerified(true);
        u.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        userRepository.save(u);
        String token = jwtService.generate(u, sessionService.create(u, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
        return new Compte(u, token);
    }

    private HttpEntity<Object> bearer(Compte c) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(c.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(h);
    }

    private void push(Compte c, String kind, String title) {
        notifications.push(c.user().getId(), title, "Corps de la notification.", kind);
    }

    private List<Map<String, Object>> liste(Compte c, String query) {
        ResponseEntity<List> res = http.exchange("/api/v1/notifications" + (query.isEmpty() ? "" : "?" + query),
                HttpMethod.GET, bearer(c), List.class);
        assertEquals(HttpStatus.OK, res.getStatusCode(), () -> "corps reçu : " + res.getBody());
        return (List<Map<String, Object>>) res.getBody();
    }

    private List<String> titres(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> String.valueOf(r.get("title"))).toList();
    }

    private Long idDe(Compte c, String title) {
        return notifications.mine(c.user()).stream()
                .filter(n -> title.equals(n.getTitle()))
                .map(InAppNotification::getId)
                .findFirst().orElseThrow();
    }

    /** `created_at` n'est pas modifiable par l'entité (updatable = false) : seul un ordre SQL le repositionne. */
    private void reculer(Compte c, String title, int jours) {
        jdbc.update("update notifications set created_at = date_sub(now(), interval ? day) where id = ?",
                jours, idDe(c, title));
    }

    @Test
    void leFiltreParTypeNeRendQueLesNotificationsDeCeType() {
        Compte sara = compte("types");
        push(sara, "STATUS", "statut-a");
        push(sara, "INTERVIEW", "entretien-a");
        push(sara, "STATUS", "statut-b");

        assertEquals(List.of("statut-b", "statut-a"), titres(liste(sara, "kind=STATUS")));
        assertEquals(List.of("entretien-a"), titres(liste(sara, "kind=INTERVIEW")));
        assertEquals(3, liste(sara, "").size(), "sans paramètre, l'historique complet doit rester servi (compteur de la cloche)");
    }

    @Test
    void leFiltreLuNonLuRenvoieLesBonsCompteurs() {
        Compte sara = compte("lecture");
        push(sara, "SLA", "a-lire");
        push(sara, "SLA", "dej-lue");
        notifications.markRead(sara.user(), idDe(sara, "dej-lue"));

        assertEquals(List.of("a-lire"), titres(liste(sara, "state=unread&kind=SLA")));
        assertEquals(List.of("dej-lue"), titres(liste(sara, "state=read&kind=SLA")));
        assertEquals(2, liste(sara, "kind=SLA").size());
    }

    @Test
    void leFiltreParDateEstAppliqueParLaBaseEtNonParLeNavigateur() {
        Compte sara = compte("dates");
        push(sara, "APPLICATION", "recente");
        push(sara, "APPLICATION", "ancienne");
        reculer(sara, "ancienne", 40);

        String depuis30jours = Instant.now().minus(30, ChronoUnit.DAYS).toString();
        assertEquals(List.of("recente"), titres(liste(sara, "kind=APPLICATION&since=" + depuis30jours)));
        assertEquals(List.of("recente", "ancienne"), titres(liste(sara, "kind=APPLICATION")));
    }

    @Test
    void lePlafondGardeLesPlusRecentesDansLeBonOrdre() {
        Compte sara = compte("plafond");
        for (int i = 1; i <= 5; i++) push(sara, "PIPELINE", "ligne-" + i);

        assertEquals(List.of("ligne-5", "ligne-4"), titres(liste(sara, "state=all&limit=2")));
        assertEquals(5, liste(sara, "state=all&limit=500").size());
    }

    @Test
    void unFiltreNePeutJamaisSortirLesNotificationsDunAutreCompte() {
        Compte sara = compte("voisine-A");
        Compte autres = compte("voisine-B");
        push(sara, "STATUS", "mienne");
        push(autres, "STATUS", "la-sienne");

        assertEquals(List.of("mienne"), titres(liste(sara, "kind=STATUS&state=all&limit=500")));
        assertFalse(titres(liste(autres, "kind=STATUS")).contains("mienne"));
    }

    @Test
    void unTypeOuUneDateIllumisibleEstRefuseSansErreurServeur() {
        Compte sara = compte("erreurs");
        push(sara, "STATUS", "ok");

        assertEquals(HttpStatus.BAD_REQUEST, http.exchange("/api/v1/notifications?kind=1+or+1=1--",
                HttpMethod.GET, bearer(sara), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange("/api/v1/notifications?since=mardi",
                HttpMethod.GET, bearer(sara), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange("/api/v1/notifications?state=peut-etre",
                HttpMethod.GET, bearer(sara), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange("/api/v1/notifications?limit=99999",
                HttpMethod.GET, bearer(sara), String.class).getStatusCode());
        assertTrue(titres(liste(sara, "kind=STATUS")).contains("ok"), "les refus précédents ne doivent rien casser");
    }

    @Test
    void lAccesSansJetonResteRefuse() {
        ResponseEntity<String> res = http.exchange("/api/v1/notifications?kind=STATUS", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        assertTrue(res.getStatusCode().is4xxClientError(), "réponse inattendue : " + res.getStatusCode());
    }
}
