package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.ApplicationService;
import ma.ensat.hirelink.service.ReliabilityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1")
public class GrowthController {

    private final ReliabilityService reliabilityService;
    private final ApplicationService applicationService;
    private final ConcurrentHashMap<String, String> referrals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> badgeViews = new ConcurrentHashMap<>();

    public GrowthController(ReliabilityService reliabilityService, ApplicationService applicationService) {
        this.reliabilityService = reliabilityService;
        this.applicationService = applicationService;
    }

    @GetMapping("/reliability/me")
    public ReliabilityService.ReliabilityScore me(@AuthenticationPrincipal User user) {
        if (user.getRole() == Role.RECRUTEUR || user.getRole() == Role.ADMINISTRATEUR) {
            return reliabilityService.forOrganization(user);
        }
        return reliabilityService.forCandidate(user);
    }

    @GetMapping("/growth/reliability-badge")
    public Map<String, Object> badge(@AuthenticationPrincipal User user) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux organisations");
        }
        var score = reliabilityService.forOrganization(user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", score);
        m.put("htmlSnippet", reliabilityService.badgeSnippet(user, score));
        m.put("views", badgeViews.getOrDefault(user.getId(), 0));
        return m;
    }

    @PostMapping("/growth/reliability-badge/view")
    public Map<String, Object> badgeView(@RequestParam Long organizationUserId) {
        int v = badgeViews.merge(organizationUserId, 1, Integer::sum);
        return Map.of("organizationUserId", organizationUserId, "views", v);
    }

    @PostMapping("/growth/referral-links")
    public Map<String, Object> createReferral(@AuthenticationPrincipal User user) {
        String code = UUID.randomUUID().toString().substring(0, 8);
        referrals.put(code, user.getEmail());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("url", "auth.html?ref=" + code);
        m.put("bonus", "1 crédit IA après complétion profil de l’invité");
        m.put("status", "CREATED");
        return m;
    }

    @GetMapping("/growth/referral-links/{code}")
    public Map<String, Object> getReferral(@PathVariable String code) {
        String email = referrals.get(code);
        if (email == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien inconnu");
        return Map.of("code", code, "referrerHint", email.replaceAll("(^.).*(@.*)$", "$1***$2"), "status", "ACTIVE");
    }

    @GetMapping("/growth/onboarding-checklist")
    public Map<String, Object> onboarding(@AuthenticationPrincipal User user) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Espace organisation");
        }
        boolean company = user.getCompany() != null && !user.getCompany().isBlank();
        boolean ice = user.getCompanyIce() != null && !user.getCompanyIce().isBlank();
        boolean verified = user.isCompanyVerified();
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("ficheEntreprise", company);
        steps.put("iceRenseigne", ice);
        steps.put("entrepriseVerifiee", verified);
        steps.put("inviterCollegue", false);
        int done = (company ? 1 : 0) + (ice ? 1 : 0) + (verified ? 1 : 0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("steps", steps);
        m.put("done", done);
        m.put("total", 4);
        m.put("percent", Math.round(100.0 * done / 4));
        return m;
    }

    /** Funnel pipeline — métriques conversion SaaS (activation / rétention). */
    @GetMapping("/growth/pipeline-funnel")
    public Map<String, Object> pipelineFunnel(@AuthenticationPrincipal User user) {
        if (user.getRole() != Role.RECRUTEUR && user.getRole() != Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Espace organisation");
        }
        return applicationService.pipelineFunnel(user);
    }
}
