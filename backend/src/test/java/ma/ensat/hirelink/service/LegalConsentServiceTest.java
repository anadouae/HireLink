package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.LegalAcceptanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalConsentServiceTest {

    @Mock LegalAcceptanceRepository repository;
    @Mock AuditService audit;
    LegalConsentService service;

    @BeforeEach
    void setUp() {
        service = new LegalConsentService(repository, audit);
        ReflectionTestUtils.setField(service, "termsVersion", "cgu:2026-11-12");
        ReflectionTestUtils.setField(service, "privacyVersion", "privacy:2026-11-12");
    }

    @Test
    void refusesMissingConsents() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assertSignupConsents(null, "privacy:2026-11-12"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void refusesStaleTermsVersion() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assertSignupConsents("cgu:old", "privacy:2026-11-12"));
        assertTrue(ex.getReason().toLowerCase().contains("cgu"));
    }

    @Test
    void recordsBothPurposes() {
        User user = new User();
        user.setId(42L);
        when(repository.existsByUserIdAndPurposeAndDocumentVersionAndGrantedTrue(anyLong(), anyString(), anyString()))
                .thenReturn(false);
        service.recordSignupConsents(user, "cgu:2026-11-12", "privacy:2026-11-12", "REGISTER");
        verify(repository, times(2)).save(any());
        verify(audit).ok(eq("CONSENT_ACCEPTED"), eq(user), eq("USER"), eq(42L), contains("TERMS="));
    }
}
