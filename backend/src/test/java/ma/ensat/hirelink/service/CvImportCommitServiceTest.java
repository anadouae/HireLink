package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CvImportCommitServiceTest {

    @Test
    void refuseUnEtatInvalideAvantTouteEcriture() {
        UserRepository users = mock(UserRepository.class);
        CandidateCareerService career = mock(CandidateCareerService.class);
        CvStorage storage = mock(CvStorage.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CvImportCommitService service = new CvImportCommitService(users, career, storage, jdbc);
        User candidate = new User();
        candidate.setId(7L);
        candidate.setRole(Role.CANDIDAT);

        assertThrows(ResponseStatusException.class, () -> service.commit(candidate, null, """
                {"items":[{"id":"skills","status":"BROKEN"}]}
                """));
        verifyNoInteractions(users, career, storage, jdbc);
    }

    @Test
    void echecApresLeFichierNeLaisseAucuneModification() throws Exception {
        UserRepository users = mock(UserRepository.class);
        CandidateCareerService career = mock(CandidateCareerService.class);
        CvStorage storage = mock(CvStorage.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CvImportCommitService service = new CvImportCommitService(users, career, storage, jdbc);
        User candidate = new User();
        candidate.setId(7L);
        candidate.setRole(Role.CANDIDAT);
        candidate.setFullName("Avant");
        candidate.setSkills("Java");
        candidate.setSoughtRoles("Développeur Java");
        candidate.setCvFileName("ancien.pdf");
        candidate.setCvStorageName("profiles/ancien.pdf");

        when(storage.save(eq("profiles"), eq("nouveau.pdf"), any(byte[].class))).thenReturn("profiles/nouveau.pdf");
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Échec du parcours"))
                .when(career).save(eq(candidate), anyMap());

        MockMultipartFile file = new MockMultipartFile(
                "file", "nouveau.pdf", "application/pdf", "pdf-test".getBytes(StandardCharsets.UTF_8));
        String payload = """
                {"profile":{"firstName":"Après","skills":"Java, Docker"},"career":{"experiences":[],"educations":[]},
                 "sections":{"skills":"Java, Docker"},"items":[{"id":"skills","status":"ACCEPTED"}]}
                """;

        assertThrows(ResponseStatusException.class, () -> service.commit(candidate, file, payload));
        verify(storage).delete("profiles/nouveau.pdf");
        verify(users, never()).save(candidate);
        verify(jdbc).update(anyString(), eq(7L), anyString());
    }
}
