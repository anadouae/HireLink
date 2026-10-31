package ma.ensat.hirelink.controller;

import jakarta.validation.constraints.Size;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.NotificationService;
import ma.ensat.hirelink.service.NotificationStreamService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService streamService;

    public NotificationController(NotificationService notificationService, NotificationStreamService streamService) {
        this.notificationService = notificationService;
        this.streamService = streamService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal User user) {
        return notificationService.mine(user).stream().map(NotificationService::toRow).toList();
    }

    /** Ticket à usage unique (30 s) à échanger contre le flux temps réel : le jeton d'accès ne passe jamais dans une URL. */
    @PostMapping("/stream-ticket")
    public Map<String, String> streamTicket(@AuthenticationPrincipal User user, Authentication auth) {
        return Map.of("ticket", streamService.issueTicket(user, (String) auth.getDetails()));
    }

    /** Flux Server-Sent Events ; l'authentification est le ticket (route ouverte dans SecurityConfig). */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam @Size(max = 64) String ticket) {
        return streamService.open(ticket);
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> read(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.markRead(user, id);
        return Map.of("status", "READ", "id", id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.delete(user, id);
        return Map.of("status", "DELETED", "id", id);
    }
}
