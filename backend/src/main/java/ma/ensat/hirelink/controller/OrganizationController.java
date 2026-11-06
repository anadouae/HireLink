package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.service.CompanyVerificationService;
import ma.ensat.hirelink.service.OrgLogoStorage;
import ma.ensat.hirelink.service.OrganizationService;
import ma.ensat.hirelink.service.OrganizationEventService;
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
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final CompanyVerificationService companyVerificationService;
    private final OrgLogoStorage orgLogoStorage;
    private final OrganizationEventService organizationEventService;
    private final ma.ensat.hirelink.service.OrganizationNoteService organizationNoteService;
    private final ma.ensat.hirelink.service.OrganizationInvitationService organizationInvitationService;
    private final ma.ensat.hirelink.service.JobOfferService jobOfferService;

    public OrganizationController(
            OrganizationService organizationService,
            CompanyVerificationService companyVerificationService,
            OrgLogoStorage orgLogoStorage,
            OrganizationEventService organizationEventService,
            ma.ensat.hirelink.service.OrganizationNoteService organizationNoteService,
            ma.ensat.hirelink.service.OrganizationInvitationService organizationInvitationService,
            ma.ensat.hirelink.service.JobOfferService jobOfferService
    ) {
        this.organizationService = organizationService;
        this.companyVerificationService = companyVerificationService;
        this.orgLogoStorage = orgLogoStorage;
        this.organizationEventService = organizationEventService;
        this.organizationNoteService = organizationNoteService;
        this.organizationInvitationService = organizationInvitationService;
        this.jobOfferService = jobOfferService;
    }

    public record InviteRequest(@jakarta.validation.constraints.Size(max = 254) String email, @jakarta.validation.constraints.Size(max = 30) String orgRole) {}

    public record EventRequest(
            @jakarta.validation.constraints.Size(max = 180) String title,
            @jakarta.validation.constraints.Size(max = 2000) String description,
            Instant startsAt,
            Integer durationMinutes,
            @jakarta.validation.constraints.Size(max = 120) String location,
            @jakarta.validation.constraints.Size(max = 500) String videoUrl,
            @jakarta.validation.constraints.Size(max = 32) String category
    ) {}

    public record OrgUpdate(
            @jakarta.validation.constraints.Size(max = 160) String name,
            @jakarta.validation.constraints.Size(max = 32) String ice,
            @jakarta.validation.constraints.Size(max = 200) String website,
            @jakarta.validation.constraints.Size(max = 80) String sector,
            @jakarta.validation.constraints.Size(max = 100) String city,
            @jakarta.validation.constraints.Size(max = 4000) String presentation,
            @jakarta.validation.constraints.Size(max = 30) String employeeRange,
            @jakarta.validation.constraints.Size(max = 300) String logoUrl
    ) {}

    @GetMapping("/public")
    public List<Map<String, Object>> listPublic() {
        return organizationService.listPublic();
    }

    /**
     * La fiche publique est adressée par le nom de l'entreprise : c'est la seule clé que voit un candidat.
     * Elle réunit en un appel les repères de l'organisation et les offres réellement en ligne, au lieu de
     * faire télécharger toute la vitrine au navigateur.
     */
    @GetMapping("/public/company")
    public Map<String, Object> publicCompanySheet(@RequestParam(required = false) String name) {
        String wanted = name == null ? "" : name.trim();
        List<ma.ensat.hirelink.dto.ApiDtos.JobOfferResponse> offers = wanted.isEmpty()
                ? List.of()
                : jobOfferService.listPublic(null).stream()
                        .filter(o -> wanted.equalsIgnoreCase(companyLabel(o)))
                        .toList();
        Map<String, Object> organization = wanted.isEmpty()
                ? null
                : organizationService.listPublic().stream()
                        .filter(row -> wanted.equalsIgnoreCase(String.valueOf(row.get("name"))))
                        .findFirst().orElse(null);
        Map<String, Object> sheet = new LinkedHashMap<>();
        sheet.put("name", organization != null ? organization.get("name") : wanted);
        sheet.put("organization", organization);
        sheet.put("offers", offers);
        return sheet;
    }

    private static String companyLabel(ma.ensat.hirelink.dto.ApiDtos.JobOfferResponse o) {
        String company = o.companyName();
        return company == null || company.isBlank() ? String.valueOf(o.recruiterName()) : company;
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @GetMapping("/mine")
    public Map<String, Object> mine(@AuthenticationPrincipal User user) {
        Organization org = organizationService.ensureForRecruiter(user);
        Membership me = organizationService.requireMembership(user);
        return toBody(org, me);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @GetMapping("/mine/members")
    public List<Map<String, Object>> members(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Membership m : organizationService.listMembers(user)) {
            boolean waiting = !m.isActive() && m.getSuspendedAt() == null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", m.getUser().getId());
            row.put("email", m.getUser().getEmail());
            row.put("fullName", m.getUser().getFullName());
            row.put("orgRole", m.getOrgRole().name());
            row.put("roleLabel", m.getOrgRole().libelleFr());
            row.put("status", m.isActive() ? "ACTIF" : (waiting ? "ATTENTE" : "PAUSE"));
            row.put("pending", waiting);
            row.put("suspended", !m.isActive() && !waiting);
            row.put("canAct", m.getOrgRole() != OrgRole.OWNER && !m.getUser().getId().equals(user.getId()));
            out.add(row);
        }
        return out;
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @GetMapping("/mine/events")
    public List<Map<String, Object>> events(@AuthenticationPrincipal User user) {
        return organizationEventService.list(user).stream().map(this::eventBody).toList();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PostMapping("/mine/events")
    public Map<String, Object> createEvent(@AuthenticationPrincipal User user,
                                            @jakarta.validation.Valid @RequestBody EventRequest body) {
        return eventBody(organizationEventService.create(user, body.title(), body.description(), body.startsAt(),
                body.durationMinutes(), body.location(), body.videoUrl(), body.category()));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PutMapping("/mine/events/{id}")
    public Map<String, Object> updateEvent(@AuthenticationPrincipal User user, @PathVariable Long id,
                                            @jakarta.validation.Valid @RequestBody EventRequest body) {
        return eventBody(organizationEventService.update(user, id, body.title(), body.description(), body.startsAt(),
                body.durationMinutes(), body.location(), body.videoUrl(), body.category()));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @DeleteMapping("/mine/events/{id}")
    public Map<String, Object> deleteEvent(@AuthenticationPrincipal User user, @PathVariable Long id) {
        organizationEventService.delete(user, id);
        return Map.of("status", "DELETED", "id", id);
    }

    public record NoteRequest(Long id, @jakarta.validation.constraints.Size(max = 120) String title,
                              @jakarta.validation.constraints.Size(max = 8000) String content,
                              String inkColor, String noteColor,
                              @jakarta.validation.constraints.Size(max = 120000) String drawingData,
                              Integer positionX, Integer positionY) {}

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @GetMapping("/mine/notes")
    public List<Map<String, Object>> notes(@AuthenticationPrincipal User user) {
        return organizationNoteService.list(user).stream().map(this::noteBody).toList();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PostMapping("/mine/notes")
    public Map<String, Object> saveNote(@AuthenticationPrincipal User user,
                                         @jakarta.validation.Valid @RequestBody NoteRequest body) {
        return noteBody(organizationNoteService.save(user, body.id(), body.title(), body.content(),
                body.inkColor(), body.noteColor(), body.drawingData(), body.positionX(), body.positionY()));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @DeleteMapping("/mine/notes/{id}")
    public Map<String, Object> deleteNote(@AuthenticationPrincipal User user, @PathVariable Long id) {
        organizationNoteService.delete(user, id);
        return Map.of("status", "DELETED", "id", id);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PostMapping("/mine/invite")
    public Map<String, Object> invite(@AuthenticationPrincipal User user, @jakarta.validation.Valid @RequestBody InviteRequest body) {
        OrgRole role = OrgRole.RECRUTEUR;
        if (body.orgRole() != null && body.orgRole().equalsIgnoreCase("HIRING_MANAGER")) {
            role = OrgRole.HIRING_MANAGER;
        }
        return organizationInvitationService.create(user, body.email(), role);
    }

    @GetMapping("/invitations/{token}")
    public Map<String, Object> invitationDetails(@PathVariable String token) {
        return organizationInvitationService.details(token);
    }

    public record AcceptInvitationRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String password) {}

    @PostMapping("/invitations/{token}/accept")
    public Map<String, Object> acceptInvitation(@PathVariable String token,
                                                 @jakarta.validation.Valid @RequestBody AcceptInvitationRequest body) {
        organizationInvitationService.accept(token, body.password());
        return Map.of("status", "ACCEPTED", "message", "Invitation acceptée. Vous pouvez maintenant vous connecter.");
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PutMapping("/mine")
    public Map<String, Object> updateMine(@AuthenticationPrincipal User user, @jakarta.validation.Valid @RequestBody OrgUpdate body) {
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

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
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

    public record IceCheckRequest(
            @jakarta.validation.constraints.Size(max = 32) String ice,
            @jakarta.validation.constraints.Size(max = 160) String name
    ) {}

    /** Verification 1 (format) + tentative d'identite — sans pretendere a une API OMPIC gratuite. */
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PostMapping("/mine/ice/check")
    public Map<String, Object> checkIce(
            @AuthenticationPrincipal User user,
            @jakarta.validation.Valid @RequestBody IceCheckRequest body
    ) {
        organizationService.requireMembership(user);
        return companyVerificationService.inspectIce(
                body.ice(),
                body.name()
        );
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
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

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @DeleteMapping("/mine/members/{userId}")
    public Map<String, Object> remove(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        organizationService.removeMember(user, userId);
        return Map.of("status", "REMOVED", "userId", userId);
    }

    public record MemberRoleRequest(@jakarta.validation.constraints.Size(max = 30) String orgRole) {}

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PatchMapping("/mine/members/{userId}/role")
    public Map<String, Object> changeMemberRole(@AuthenticationPrincipal User user, @PathVariable Long userId,
                                                @jakarta.validation.Valid @RequestBody MemberRoleRequest body) {
        Membership m = organizationService.changeMemberRole(user, userId, parseRole(body.orgRole()));
        return Map.of("userId", userId, "orgRole", m.getOrgRole().name(), "roleLabel", m.getOrgRole().libelleFr());
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PatchMapping("/mine/members/{userId}/suspend")
    public Map<String, Object> suspendMember(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        organizationService.suspendMember(user, userId);
        return Map.of("userId", userId, "suspended", true);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PatchMapping("/mine/members/{userId}/reinstate")
    public Map<String, Object> reinstateMember(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        organizationService.reinstateMember(user, userId);
        return Map.of("userId", userId, "suspended", false);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RECRUTEUR')")
    @PatchMapping("/mine/members/{userId}/ownership")
    public Map<String, Object> transferOwnership(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        Membership m = organizationService.transferOwnership(user, userId);
        return Map.of("userId", userId, "orgRole", m.getOrgRole().name(), "roleLabel", m.getOrgRole().libelleFr());
    }

    /** Rôle d'espace envoyé par le front : RECRUTEUR ou HIRING_MANAGER, jamais OWNER (il se transfère). */
    private static OrgRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle manquant");
        }
        try {
            return OrgRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle inconnu : " + raw);
        }
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
        ma.ensat.hirelink.model.PlanTier tier = org.getPlanTier() != null ? org.getPlanTier() : ma.ensat.hirelink.model.PlanTier.ACCES;
        int maxMembers = tier == ma.ensat.hirelink.model.PlanTier.PREMIUM ? 25 : 5;
        int memberCount = organizationService.memberCount(org);
        m.put("maxMembers", maxMembers);
        m.put("memberCount", memberCount);
        m.put("seatsRemaining", Math.max(0, maxMembers - memberCount));
        m.put("myRole", me.getOrgRole().name());
        m.put("website", org.getWebsite() == null ? "" : org.getWebsite());
        m.put("logoUrl", org.getLogoUrl() == null ? "" : org.getLogoUrl());
        m.put("sector", org.getSector() == null ? "" : org.getSector());
        m.put("city", org.getCity() == null ? "" : org.getCity());
        m.put("employees", org.getEmployeeRange() == null ? "" : org.getEmployeeRange());
        m.put("presentation", org.getPresentation() == null ? "" : org.getPresentation());
        m.put("canPublish", org.isVerified());
        m.put("iceFormatValid", org.getIceFormatValid());
        m.put("iceLegalName", org.getIceLegalName() == null ? "" : org.getIceLegalName());
        m.put("iceAddress", org.getIceAddress() == null ? "" : org.getIceAddress());
        m.put("iceSource", org.getIceSource() == null ? "" : org.getIceSource());
        m.put("iceCheckedAt", org.getIceCheckedAt() == null ? "" : org.getIceCheckedAt().toString());
        return m;
    }

    private Map<String, Object> noteBody(ma.ensat.hirelink.model.OrganizationNote n) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", n.getId()); out.put("title", n.getTitle()); out.put("content", n.getContent());
        out.put("inkColor", n.getInkColor()); out.put("noteColor", n.getNoteColor());
        out.put("drawingData", n.getDrawingData()); out.put("positionX", n.getPositionX());
        out.put("positionY", n.getPositionY()); out.put("updatedAt", n.getUpdatedAt());
        return out;
    }

    private Map<String, Object> eventBody(OrganizationEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", event.getId());
        body.put("title", event.getTitle());
        body.put("description", event.getDescription());
        body.put("startsAt", event.getStartsAt());
        body.put("durationMinutes", event.getDurationMinutes());
        body.put("location", event.getLocation());
        body.put("videoUrl", event.getVideoUrl());
        body.put("category", event.getCategory());
        body.put("createdBy", event.getCreatedBy().getFullName());
        return body;
    }
}
