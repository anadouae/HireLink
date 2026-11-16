package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandidateCareerServiceTest {

    @Test
    void saveAndGetExperiencesAndEducations() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        CandidateCareerService svc = new CandidateCareerService(repo);

        User u = new User();
        u.setRole(Role.CANDIDAT);

        Map<String, Object> saved = svc.save(u, Map.of(
                "jobTitle", "Développeuse",
                "country", "Maroc",
                "presentation", "Passionnée par le backend.",
                "experiences", List.of(Map.of(
                        "title", "Dev Java",
                        "company", "Acme",
                        "location", "Tanger",
                        "startDate", "2022-01",
                        "endDate", "",
                        "current", true,
                        "missions", "API REST",
                        "achievements", "Latency -30%"
                )),
                "educations", List.of(Map.of(
                        "school", "ENSAT",
                        "degree", "Ingénieur",
                        "domain", "Informatique",
                        "startDate", "2018-09",
                        "endDate", "2023-06",
                        "description", ""
                ))
        ));

        assertEquals("Développeuse", saved.get("jobTitle"));
        assertEquals("Maroc", saved.get("country"));
        assertNotNull(u.getProfileExperiences());
        assertTrue(u.getProfileExperiences().contains("Acme"));
        assertTrue(u.getProfileEducations().contains("ENSAT"));
    }

    @Test
    void rejectsRecruiter() {
        CandidateCareerService svc = new CandidateCareerService(mock(UserRepository.class));
        User r = new User();
        r.setRole(Role.RECRUTEUR);
        assertThrows(ResponseStatusException.class, () -> svc.get(r));
    }
}
