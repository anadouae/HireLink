package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.service.CompanyVerificationService;
import ma.ensat.hirelink.service.OrgLogoStorage;
import ma.ensat.hirelink.service.OrganizationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final CompanyVerificationService companyVerificationService;
    private final OrgLogoStorage orgLogoStorage;

    public OrganizationController(
            OrganizationService organizationService,
            CompanyVerificationService companyVerificationService,
            OrgLogoStorage orgLogoStorage
    ) {
        this.organizationService = organizationService;
        this.companyVerificationService = companyVerificationService;
        this.orgLogoStorage = orgLogoStorage;
    }

    public record InviteRequest(String email, String orgRole) {}

    public record OrgUpdate(
            String name,
            String ice,
            String website,
            String sector,
            String city,
            String presentation,
            String employeeRange,
            String logoUrl
    ) {}

    @GetMapping("/public")
    public List<Map<String, Object>> listPublic() {
        return organizationService.listPublic();
    }

    @GetMapping("/mine")
    public Map<String, Object> mine(@AuthenticationPrincipal User user) {
        Organization org = organizationService.ensureForRecruiter(user);
        Membership me = organizationService.requireMembership(user);
        return toBody(org, me);
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

    @PutMapping("/mine")
    public Map<String, Object> updateMine(@AuthenticationPrincipal User user, @RequestBody OrgUpdate body) {
        Organization org = organizationService.updateMine(
                user,
                body.name(),
                body.ice(),
                body.website(),
                body.sector(),
                body.city(),
                body.presentation(),
                body.employeeRange(),
                body.logoUrl()
        );
        Membership me = organizationService.requireMembership(user);
        return toBody(org, me);
    }

    @PostMapping(value = "/mine/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadLogo(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file
    ) {
        Organization org = organizationService.ensureForRecruiter(user);
        try {
            String stored = orgLogoStorage.save(org.getId(), file.getOriginalFilename(), file.getBytes());
            String publicUrl = "/api/v1/media/org-logos/" + stored;
            org = organizationService.setLogoUrl(user, publicUrl);
            Membership me = organizationService.requireMembership(user);
            return toBody(org, me);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload du logo impossible");
        }
    }

    @PostMapping("/mine/verify")
    public Map<String, Object> recheck(@AuthenticationPrincipal User user) {
        Organization org = organizationService.recheck(user);
        Membership me = organizationService.requireMembership(user);
        Map<String, Object> body = toBody(org, me);
        body.put("message", companyVerificationService.publishBlockReason(
                org.isVerified() ? "REGISTRY_MATCH" : org.getVerificationStatus()
        ));
        if (org.isVerified()) {
            body.put("message", "Entreprise vérifiée. Vous pouvez publier des offres.");
        }
        return body;
    }

    @DeleteMapping("/mine/members/{userId}")
    public Map<String, Object> remove(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        organizationService.removeMember(user, userId);
        return Map.of("status", "REMOVED", "userId", userId);
    }

    private Map<String, Object> toBody(Organization org, Membership me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", org.getId());
        m.put("name", org.getName());
        m.put("ice", org.getIce());
        m.put("verified", org.isVerified());
        m.put("verificationStatus", org.getVerificationStatus() == null ? "" : org.getVerificationStatus());
        m.put("verificationMethod", org.getVerificationMethod() == null ? "" : org.getVerificationMethod());
        m.put("statusLabel", companyVerificationService.statusLabel(org.getVerificationStatus(), org.isVerified()));
        m.put("planTier", org.getPlanTier().name());
        m.put("myRole", me.getOrgRole().name());
        m.put("website", org.getWebsite() == null ? "" : org.getWebsite());
        m.put("logoUrl", org.getLogoUrl() == null ? "" : org.getLogoUrl());
        m.put("sector", org.getSector() == null ? "" : org.getSector());
        m.put("city", org.getCity() == null ? "" : org.getCity());
        m.put("employees", org.getEmployeeRange() == null ? "" : org.getEmployeeRange());
        m.put("presentation", org.getPresentation() == null ? "" : org.getPresentation());
        m.put("canPublish", org.isVerified());
        return m;
    }
}
