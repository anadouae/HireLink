package ma.ensat.hirelink.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import ma.ensat.hirelink.model.SupportRequest;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.SupportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Page Support : tout visiteur ou utilisateur peut écrire (signalement, précision, question).
 * La lecture des demandes est réservée à l'administration.
 */
@RestController
public class SupportController {

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    public record SupportBody(
            @Size(max = 20) String topic,
            @Size(max = 120) String name,
            @Size(max = 160) String email,
            @Size(max = 160) String subject,
            @Size(max = 4000) String message,
            @Size(max = 300) String reference,
            @Size(max = 200) String website
    ) {}

    @PostMapping("/api/v1/support")
    public Map<String, Object> submit(@AuthenticationPrincipal User user, @Valid @RequestBody SupportBody body) {
        return support.submit(user, new SupportService.Input(
                body.topic(), body.name(), body.email(), body.subject(), body.message(), body.reference(), body.website()));
    }

    @GetMapping("/api/v1/admin/support")
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "25") int size) {
        return support.list(status, page, size);
    }

    public record StatusBody(String status) {}

    @PatchMapping("/api/v1/admin/support/{id}")
    public SupportRequest setStatus(@PathVariable Long id, @RequestBody StatusBody body) {
        return support.setStatus(id, body == null ? null : body.status());
    }
}
