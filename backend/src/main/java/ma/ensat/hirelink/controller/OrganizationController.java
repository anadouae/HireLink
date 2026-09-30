package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.service.OrganizationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    public record InviteRequest(String email, String orgRole) {}

    @GetMapping("/mine")
    public Map<String, Object> mine(@AuthenticationPrincipal User user) {
        Organization org = organizationService.ensureForRecruiter(user);
        Membership me = organizationService.requireMembership(user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", org.getId());
        m.put("name", org.getName());
        m.put("ice", org.getIce());
        m.put("verified", org.isVerified());
        m.put("planTier", org.getPlanTier().name());
        m.put("myRole", me.getOrgRole().name());
        return m;
    }

    @GetMapping("/mine/members")
    public List<Map<String, Object>> members(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Membership m : organizationService.listMembers(user)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", m.getUser().getId());
            row.put("email", m.getUser().getEmail());
            row.put("fullName", m.getUser().getFullName());
            row.put("orgRole", m.getOrgRole().name());
            out.add(row);
        }
        return out;
    }

    @PostMapping("/mine/invite")
    public Map<String, Object> invite(@AuthenticationPrincipal User user, @RequestBody InviteRequest body) {
        OrgRole role = OrgRole.RECRUTEUR;
        if (body.orgRole() != null && body.orgRole().equalsIgnoreCase("HIRING_MANAGER")) {
            role = OrgRole.HIRING_MANAGER;
        }
        Membership m = organizationService.invite(user, body.email(), role);
        return Map.of(
                "userId", m.getUser().getId(),
                "email", m.getUser().getEmail(),
                "orgRole", m.getOrgRole().name(),
                "status", "OK"
        );
    }

    @DeleteMapping("/mine/members/{userId}")
    public Map<String, Object> remove(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        organizationService.removeMember(user, userId);
        return Map.of("status", "REMOVED", "userId", userId);
    }
}
