package ma.ensat.hirelink.service;

import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.AuthThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Vérification « à la LinkedIn » : le badge vient de l'e-mail professionnel, l'ICE est facultatif. */
class CompanyVerificationServiceTest {

    private CompanyVerificationService service;

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        when(users.findAllByCompanyIce(anyString())).thenReturn(List.of());
        service = new CompanyVerificationService(users, new IceFormatValidator(), new IceIdentityLookup("", ""), mock(AuthThrottle.class));
    }

    @Test
    void unEmailProfessionnelSuffitSansIce() {
        var r = service.verify(null, "Atlas Digital", 7L, "rh@atlas-digital.ma");
        assertTrue(r.verified());
        assertEquals("DOMAIN_EMAIL", r.status());
        assertNull(r.ice());
    }

    @Test
    void uneMessagerieGrandPublicNeDonnePasLeBadge() {
        var r = service.verify(null, "Atlas Digital", 7L, "atlas.rh@gmail.com");
        assertFalse(r.verified());
        assertEquals("NEED_CORPORATE_EMAIL", r.status());
        assertEquals("E-mail professionnel requis", service.statusLabel(r.status(), r.verified()));
    }

    @Test
    void uneAdresseUniversitaireNEstPasProfessionnelle() {
        assertFalse(service.isCorporateEmail("etudiant@etu.uae.ac.ma"));
        assertFalse(service.isCorporateEmail("x@university.edu"));
        assertTrue(service.isCorporateEmail("contact@ocp.ma"));
    }

    @Test
    void unIceSaisiEstControleEnFormat() {
        assertThrows(ResponseStatusException.class, () -> service.verify("12AB", "Atlas Digital", 7L, "rh@atlas.ma"));
    }

    @Test
    void leNomDeLEntrepriseResteObligatoire() {
        assertThrows(ResponseStatusException.class, () -> service.verify(null, " ", 7L, "rh@atlas.ma"));
    }

    @Test
    void lesMessagesNeCitentNiRegistreNiAdministration() {
        for (String st : List.of("NEED_CORPORATE_EMAIL", "WAITING_REGISTRY", "NAME_MISMATCH", "PENDING")) {
            String msg = service.publishBlockReason(st).toLowerCase();
            assertFalse(msg.contains("registre"), msg);
            assertFalse(msg.contains("admin"), msg);
        }
    }
}
