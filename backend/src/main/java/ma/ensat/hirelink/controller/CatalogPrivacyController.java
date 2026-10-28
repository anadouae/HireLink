package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.*;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.service.OrganizationService;
import ma.ensat.hirelink.service.PrivacyService;
import ma.ensat.hirelink.service.TimelineService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogPrivacyController {

    private final TimelineService timelineService;
    private final PrivacyService privacyService;
    private final ApplicationRepository applicationRepository;
    private final OrganizationService organizationService;
    private final ma.ensat.hirelink.service.AuditService audit;

    public CatalogPrivacyController(
            TimelineService timelineService,
            PrivacyService privacyService,
            ApplicationRepository applicationRepository,
            OrganizationService organizationService,
            ma.ensat.hirelink.service.AuditService audit
    ) {
        this.audit = audit;
        this.organizationService = organizationService;
        this.timelineService = timelineService;
        this.privacyService = privacyService;
        this.applicationRepository = applicationRepository;
    }

    // GET /refusal-reasons → RefusalReasonController (évite mapping ambigu)

    @GetMapping("/applications/{id}/timeline")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> timeline(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        boolean ok = user.getRole() == Role.ADMINISTRATEUR
                || app.getCandidate().getId().equals(user.getId())
                || user.getId().equals(app.getAssignedHmUserId())
                || (user.getRole() == Role.RECRUTEUR && organizationService.canManageOffer(user, app.getJobOffer()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès historique refusé (R03)");
        }
        boolean candidateView = app.getCandidate().getId().equals(user.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (ApplicationStatusHistory h : timelineService.forApplication(id)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", h.getFromStatus());
            row.put("to", h.getToStatus());
            row.put("actorUserId", h.getActorUserId());
            // notes et scores d'évaluation : réservés à l'équipe de recrutement
            if (!candidateView) row.put("payload", h.getPayloadJson());
            row.put("at", h.getCreatedAt().toString());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/me/export")
    public Map<String, Object> export(@AuthenticationPrincipal User user) {
        audit.ok("DATA_EXPORT", user, "USER", user.getId(), "export RGPD");
        return privacyService.exportFor(user);
    }

    @PostMapping("/me/consents")
    public Map<String, Object> consent(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body
    ) {
        String purpose = String.valueOf(body.getOrDefault("purpose", "recruitment"));
        if (!purpose.matches("[a-z_]{3,40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finalité invalide");
        }
        boolean granted = !Boolean.FALSE.equals(body.get("granted"));
        var c = privacyService.recordConsent(user, purpose, granted);
        return Map.of("purpose", c.getPurpose(), "granted", c.isGranted(), "at", c.getCreatedAt().toString());
    }
}
