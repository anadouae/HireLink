package ma.ensat.hirelink.integration;

import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.OrgRole;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolation inter-organisations vérifiée sur la chaîne réelle : HTTP → garde-fou → service → base.
 * Les fixtures sont construites par le code de production (createOwned / joinOrganization), jamais par
 * une insertion manuelle, et chaque test crée ses propres comptes pour rester indépendante.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfferIsolationIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired UserRepository userRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired MembershipRepository membershipRepository;
    @Autowired JobOfferRepository jobOfferRepository;
    @Autowired OrganizationService organizationService;
    @Autowired SessionService sessionService;
    @Autowired JwtService jwtService;

    private record Equipe(Long userId, Long orgId, String token) {}

    /** Un recruteur « entreprise » complet : compte RECRUTEUR + espace OWNER créé par le code métier. */
    private Equipe entreprise(String nom) {
        String graine = Long.toHexString(System.nanoTime()) + (long) nom.hashCode();
        User owner = new User();
        owner.setEmail("owner-" + graine + "@test.hirelink.local");
        owner.setFullName("Propriétaire " + nom);
        owner.setRole(Role.RECRUTEUR);
        owner.setPlanTier(PlanTier.ACCES);
        owner.setCompany(nom);
        owner.setCompanyVerified(true);
        owner.setEmailVerified(true);
        owner.setProfileComplete(true);
        owner.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        userRepository.save(owner);
        Organization org = organizationService.createOwned(owner, nom, null);
        String token = jwtService.generate(owner, sessionService.create(owner, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
        return new Equipe(owner.getId(), org.getId(), token);
    }

    private HttpEntity<Object> bearer(Equipe e) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(e.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(h);
    }

    private HttpEntity<Object> bearer(Equipe e, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(e.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private Map<String, Object> offreBody(String title) {
        return Map.of(
                "title", title,
                "description", "Conception et maintien d'applications web. Poste basé à Tétouan, en hybride.",
                "contractType", "CDI",
                "published", true
        );
    }

    private Long publier(Equipe e, String title) {
        ResponseEntity<Map> res = http.exchange("/api/v1/offers", HttpMethod.POST, bearer(e, offreBody(title)), Map.class);
        assertEquals(HttpStatus.OK, res.getStatusCode(), () -> "publication refusée : " + res.getBody());
        return ((Number) res.getBody().get("id")).longValue();
    }

    private List<Long> mesOffres(Equipe e) {
        ResponseEntity<List> res = http.exchange("/api/v1/offers/mine", HttpMethod.GET, bearer(e), List.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        return ((List<Map<String, Object>>) res.getBody()).stream()
                .map(row -> ((Number) row.get("id")).longValue())
                .toList();
    }

    @Test
    void lOffrePorteSonOrganisationDesLaCreation() {
        Equipe atlas = entreprise("Atlas Intégration");
        Long offerId = publier(atlas, "Développeuse Java Spring");
        JobOffer offer = jobOfferRepository.findById(offerId).orElseThrow();
        assertNotNull(offer.getOrganization(), "l'organisation n'a pas été gravée à la création");
        assertEquals(atlas.orgId(), offer.getOrganization().getId());
        assertTrue(mesOffres(atlas).contains(offerId),
                "l'auteur ne voit pas sa propre offre");
    }

    @Test
    void uneAutreOrganisationNeLitNiNeModifiePasUneOffreEtrangere() {
        Equipe atlas = entreprise("Atlas Voisinage");
        Equipe nova = entreprise("Nova Conseil");
        Long offerId = publier(atlas, "Ingénieure QA");

        ResponseEntity<String> put = http.exchange("/api/v1/offers/" + offerId,
                HttpMethod.PUT, bearer(nova, offreBody("Rebaptisée par l'intrus")), String.class);
        assertEquals(HttpStatus.FORBIDDEN, put.getStatusCode(), () -> "corps reçu : " + put.getBody());

        ResponseEntity<String> archive = http.exchange("/api/v1/offers/" + offerId + "/archive",
                HttpMethod.POST, bearer(nova), String.class);
        assertEquals(HttpStatus.FORBIDDEN, archive.getStatusCode());

        assertFalse(mesOffres(nova).contains(offerId),
                "l'offre d'une autre organisation est apparue dans /offers/mine");

        JobOffer apres = jobOfferRepository.findById(offerId).orElseThrow();
        assertEquals("Ingénieure QA", apres.getTitle(), "l'intrusion a modifié l'offre");
    }

    @Test
    void lAuteurSortiDeSonOrganisationNeGerePlusSesOffres() {
        Equipe atlas = entreprise("Atlas Départ");
        Long offerId = publier(atlas, "Analyste de données");

        // Démission : l'appartenance active est retirée (ce que fait l'administration), le compte et sa
        // session restent valides — c'est exactement le cas où l'accès « auteur de l'offre » suffisait avant A1.
        Membership m = membershipRepository.findByOrganizationIdAndUserId(atlas.orgId(), atlas.userId())
                .orElseThrow();
        m.setActive(false);
        membershipRepository.save(m);

        ResponseEntity<String> put = http.exchange("/api/v1/offers/" + offerId,
                HttpMethod.PUT, bearer(atlas, offreBody("Rebaptisée après départ")), String.class);
        assertEquals(HttpStatus.FORBIDDEN, put.getStatusCode(), () -> "corps reçu : " + put.getBody());

        ResponseEntity<String> archive = http.exchange("/api/v1/offers/" + offerId + "/archive",
                HttpMethod.POST, bearer(atlas), String.class);
        assertEquals(HttpStatus.FORBIDDEN, archive.getStatusCode());

        JobOffer offre = jobOfferRepository.findById(offerId).orElseThrow();
        assertEquals(offerId, offre.getId());
        assertEquals(atlas.orgId(), offre.getOrganization().getId(),
                "l'offre a suivi son auteur hors de son organisation");
    }

    @Test
    void unResponsableDuPosteNeGerePasLesOffresDeSonOrganisation() {
        Equipe atlas = entreprise("Atlas Responsable du poste");
        User hm = atlasEquipeMember(atlas, "hm");
        hm.setRole(Role.RECRUTEUR);
        userRepository.save(hm);
        organizationService.joinOrganization(hm, organizationRepository.findById(atlas.orgId()).orElseThrow(), OrgRole.HIRING_MANAGER);
        String token = jwtService.generate(hm, sessionService.create(hm, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
        Equipe invite = new Equipe(hm.getId(), atlas.orgId(), token);

        Long offerId = publier(atlas, "Développeuse Front");
        ResponseEntity<String> put = http.exchange("/api/v1/offers/" + offerId,
                HttpMethod.PUT, bearer(invite, offreBody("Rebaptisée par le responsable du poste")), String.class);
        assertEquals(HttpStatus.FORBIDDEN, put.getStatusCode(), () -> "corps reçu : " + put.getBody());
    }

    @Test
    void leMenuTroisPointsSuitLeCycleDeVie() {
        Equipe atlas = entreprise("Atlas Cycle");
        Long id = publier(atlas, "Offre à faire vivre");

        Map<String, Object> masquee = geste(atlas, id, "/unpublish", null);
        assertEquals(false, masquee.get("published"));
        Map<String, Object> republiee = geste(atlas, id, "/publish", null);
        assertEquals(true, republiee.get("published"));
        Map<String, Object> prolongee = geste(atlas, id, "/extend", Map.of("days", 14));
        assertNotNull(prolongee.get("deadline"));
        Map<String, Object> cloturee = geste(atlas, id, "/close", null);
        assertNotNull(cloturee.get("closedAt"));
        Map<String, Object> rouverte = geste(atlas, id, "/reopen", null);
        assertEquals(null, rouverte.get("closedAt"));
        Map<String, Object> archivee = geste(atlas, id, "/archive", null);
        assertNotNull(archivee.get("archivedAt"));
        Map<String, Object> restauree = geste(atlas, id, "/unarchive", null);
        assertEquals(null, restauree.get("archivedAt"));

        Map<String, Object> copie = geste(atlas, id, "/duplicate", null);
        assertEquals(false, copie.get("published"));
        Long copieId = ((Number) copie.get("id")).longValue();
        assertEquals(atlas.orgId(), jobOfferRepository.findById(copieId).orElseThrow().getOrganization().getId());
        ResponseEntity<Map> supprimee = http.exchange("/api/v1/offers/" + copieId, HttpMethod.DELETE, bearer(atlas), Map.class);
        assertEquals(HttpStatus.OK, supprimee.getStatusCode(), () -> String.valueOf(supprimee.getBody()));

        User candidate = atlasEquipeMember(atlas, "cand");
        candidate.setRole(Role.CANDIDAT);
        userRepository.save(candidate);
        String candidateToken = jwtService.generate(candidate, sessionService.create(candidate, new ClientInfo("127.0.0.1", "itest")).session().getId().toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(candidateToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> candidature = http.exchange("/api/v1/applications", HttpMethod.POST,
                new HttpEntity<>(Map.of("jobOfferId", id, "cvSource", "UPLOAD", "cvFileName", "cv.txt"), headers), Map.class);
        assertEquals(HttpStatus.OK, candidature.getStatusCode(), () -> String.valueOf(candidature.getBody()));
        ResponseEntity<String> bloquee = http.exchange("/api/v1/offers/" + id, HttpMethod.DELETE, bearer(atlas), String.class);
        assertEquals(HttpStatus.CONFLICT, bloquee.getStatusCode(), () -> bloquee.getBody());
    }

    private Map<String, Object> geste(Equipe e, Long id, String path, Object body) {
        ResponseEntity<Map> res = http.exchange("/api/v1/offers/" + id + path, HttpMethod.POST,
                body == null ? bearer(e) : bearer(e, body), Map.class);
        assertEquals(HttpStatus.OK, res.getStatusCode(), () -> path + " " + res.getBody());
        return res.getBody();
    }

    private User atlasEquipeMember(Equipe atlas, String prefix) {
        String graine = Long.toHexString(System.nanoTime());
        User u = new User();
        u.setEmail(prefix + "-" + graine + "@test.hirelink.local");
        u.setFullName("Collègue " + prefix);
        u.setRole(Role.RECRUTEUR);
        u.setPlanTier(PlanTier.ACCES);
        u.setEmailVerified(true);
        u.setPasswordHash("$2a$10$integrationtestnopasswordhashunused000000000000000000");
        userRepository.save(u);
        return u;
    }
}
