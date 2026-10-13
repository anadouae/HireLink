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

    public record RefusalBody(String code, String label, Boolean active) {}

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "false") boolean all) {
        return all ? service.listAll() : service.listActive();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal User user, @RequestBody RefusalBody body) {
        boolean active = body.active() == null || body.active();
        return service.create(user, body.code(), body.label(), active);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody RefusalBody body
    ) {
        return service.update(user, id, body.code(), body.label(), body.active());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
        return Map.of("status", "DELETED", "id", id);
    }
}
