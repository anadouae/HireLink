package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.RefusalReasonService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD complet — Motifs de refus (entité 3 pour Séance 4).
 */
@RestController
@RequestMapping("/api/v1/refusal-reasons")
public class RefusalReasonController {

    private final RefusalReasonService service;

    public RefusalReasonController(RefusalReasonService service) {
        this.service = service;
    }

    public record RefusalBody(@jakarta.validation.constraints.Size(max = 40) String code, @jakarta.validation.constraints.Size(max = 160) String label, Boolean active) {}

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "false") boolean all) {
        if (all && (user == null || user.getRole() != ma.ensat.hirelink.model.Role.ADMINISTRATEUR)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Réservé à l'administration");
        }
        return all ? service.listAll() : service.listActive();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null || user.getRole() != ma.ensat.hirelink.model.Role.ADMINISTRATEUR) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Réservé à l'administration");
        }
        return service.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal User user, @jakarta.validation.Valid @RequestBody RefusalBody body) {
        boolean active = body.active() == null || body.active();
        return service.create(user, body.code(), body.label(), active);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody RefusalBody body
    ) {
        return service.update(user, id, body.code(), body.label(), body.active());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
        return Map.of("status", "DELETED", "id", id);
    }
}
