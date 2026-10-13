package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.InAppNotification;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InAppNotification n : notificationService.mine(user)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", n.getId());
            row.put("title", n.getTitle());
            row.put("body", n.getBody());
            row.put("kind", n.getKind());
            row.put("read", n.isReadFlag());
            row.put("at", n.getCreatedAt().toString());
            out.add(row);
        }
        return out;
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> read(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.markRead(user, id);
        return Map.of("status", "READ", "id", id);
    }

    public record CreateNotif(String title, String body, String kind) {}

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal User user, @RequestBody CreateNotif body) {
        InAppNotification n = notificationService.create(user, body.title(), body.body(), body.kind());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n.getId());
        row.put("title", n.getTitle());
        row.put("body", n.getBody());
        row.put("kind", n.getKind());
        row.put("read", n.isReadFlag());
        row.put("at", n.getCreatedAt().toString());
        return row;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.delete(user, id);
        return Map.of("status", "DELETED", "id", id);
    }
}
