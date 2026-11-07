package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.CompanyFollowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Entreprises suivies par le candidat connecté. Aucune route n'expose la liste des abonnés :
 * l'entreprise ne voit qu'un compteur, jamais des identités.
 */
@RestController
@RequestMapping("/api/v1/follows")
public class CompanyFollowController {

    private final CompanyFollowService companyFollowService;

    public CompanyFollowController(CompanyFollowService companyFollowService) {
        this.companyFollowService = companyFollowService;
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('CANDIDAT')")
    @GetMapping
    public List<Map<String, Object>> mine(@AuthenticationPrincipal User user) {
        return companyFollowService.mine(user);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('CANDIDAT')")
    @PostMapping("/{organizationId}")
    public Map<String, Object> follow(@AuthenticationPrincipal User user, @PathVariable Long organizationId) {
        return companyFollowService.follow(user, organizationId);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('CANDIDAT')")
    @DeleteMapping("/{organizationId}")
    public Map<String, Object> unfollow(@AuthenticationPrincipal User user, @PathVariable Long organizationId) {
        return companyFollowService.unfollow(user, organizationId);
    }
}
