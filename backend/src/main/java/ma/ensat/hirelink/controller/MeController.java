package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.dto.ApiDtos;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.CompanyVerificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserRepository userRepository;
    private final CompanyVerificationService companyVerificationService;

    public MeController(UserRepository userRepository, CompanyVerificationService companyVerificationService) {
        this.userRepository = userRepository;
        this.companyVerificationService = companyVerificationService;
    }

    @GetMapping
    public ApiDtos.UserProfile me(@AuthenticationPrincipal User user) {
        return toProfile(user);
    }

    @PatchMapping
    public ApiDtos.UserProfile update(
            @AuthenticationPrincipal User user,
            @RequestBody ApiDtos.UpdateProfile body
    ) {
        if (body.fullName() != null && !body.fullName().isBlank()) {
            user.setFullName(body.fullName().trim());
        }
        if (body.skills() != null) {
            user.setSkills(body.skills().trim());
        }
        if (body.company() != null) {
            user.setCompany(body.company().trim());
        }
        if (body.soughtRoles() != null) {
            user.setSoughtRoles(body.soughtRoles().trim());
        }
        if (body.preferredLocations() != null) {
            user.setPreferredLocations(body.preferredLocations().trim());
        }
        if (body.preferredContracts() != null) {
            user.setPreferredContracts(body.preferredContracts().trim());
        }
        if (body.experienceLevel() != null) {
            user.setExperienceLevel(body.experienceLevel().trim());
        }
        if (user.getRole() == Role.RECRUTEUR && body.companyIce() != null && !body.companyIce().isBlank()) {
            var v = companyVerificationService.verify(
                    body.companyIce(),
                    body.company() != null ? body.company() : user.getCompany(),
                    user.getId(),
                    user.getEmail()
            );
            user.setCompanyIce(v.ice());
            user.setCompany(v.companyName());
            user.setCompanyVerified(v.verified());
            user.setVerificationStatus(v.status());
        }
        boolean complete = user.getFullName() != null && !user.getFullName().isBlank();
        if (user.getRole() == Role.CANDIDAT) {
            complete = complete
                    && user.getSkills() != null && !user.getSkills().isBlank()
                    && user.getSoughtRoles() != null && !user.getSoughtRoles().isBlank();
        }
        if (user.getRole() == Role.RECRUTEUR) {
            // Profil « complet » = infos renseignées ; badge vérifié = droit de publier (séparé).
            complete = complete && user.getCompanyIce() != null
                    && user.getCompany() != null && !user.getCompany().isBlank();
        }
        user.setProfileComplete(complete);
        userRepository.save(user);
        return toProfile(user);
    }

    private ApiDtos.UserProfile toProfile(User u) {
        String vs = u.getVerificationStatus();
        if (vs == null || vs.isBlank()) {
            vs = u.isCompanyVerified() ? "REGISTRY_MATCH" : "PENDING";
        }
        return new ApiDtos.UserProfile(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getRole().name(),
                u.isMustSetPassword(),
                u.isEmailVerified(),
                u.isProfileComplete(),
                u.getSkills(),
                u.getCompany(),
                u.getCompanyIce(),
                u.isCompanyVerified(),
                vs,
                u.getSoughtRoles(),
                u.getPreferredLocations(),
                u.getPreferredContracts(),
                u.getExperienceLevel(),
                u.getCreatedAt()
        );
    }
}
