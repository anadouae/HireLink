package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final JobOfferRepository jobOfferRepository;
    private final ApplicationRepository applicationRepository;

    public AdminController(
            UserRepository userRepository,
            JobOfferRepository jobOfferRepository,
            ApplicationRepository applicationRepository
    ) {
        this.userRepository = userRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/stats")
    public ApiDtos.AdminStats stats(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        return new ApiDtos.AdminStats(
                userRepository.count(),
                userRepository.countByRole(Role.CANDIDAT),
                userRepository.countByRole(Role.RECRUTEUR),
                jobOfferRepository.count(),
                applicationRepository.count(),
                jobOfferRepository.countByPublishedTrue()
        );
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@AuthenticationPrincipal User user) {
        requireAdmin(user);
        return userRepository.findAll().stream().map(u -> Map.<String, Object>of(
                "id", u.getId(),
                "email", u.getEmail(),
                "fullName", u.getFullName(),
                "role", u.getRole().name(),
                "active", u.isActive(),
                "mustSetPassword", u.isMustSetPassword(),
                "emailVerified", u.isEmailVerified()
        )).toList();
    }

    @PatchMapping("/users/{id}/active")
    public Map<String, Object> toggleActive(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        requireAdmin(admin);
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User introuvable"));
        u.setActive(Boolean.TRUE.equals(body.get("active")));
        userRepository.save(u);
        return Map.of("id", u.getId(), "active", u.isActive());
    }

    @PatchMapping("/users/{id}/verify-company")
    public Map<String, Object> verifyCompany(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id
    ) {
        requireAdmin(admin);
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User introuvable"));
        u.setCompanyVerified(true);
        u.setVerificationStatus("REGISTRY_MATCH");
        userRepository.save(u);
        return Map.of(
                "id", u.getId(),
                "companyVerified", true,
                "verificationStatus", u.getVerificationStatus(),
                "company", u.getCompany() == null ? "" : u.getCompany()
        );
    }

    private void requireAdmin(User user) {
        if (user == null || user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin uniquement");
        }
    }
}
