package ma.ensat.hirelink.controller;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.NotificationService;
import ma.ensat.hirelink.service.NotificationStreamService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {

    private static final int DEFAULT_LIMIT = 300;
    private static final int MAX_LIMIT = 500;

    private final NotificationService notificationService;
    private final NotificationStreamService streamService;

    public NotificationController(NotificationService notificationService, NotificationStreamService streamService) {
        this.notificationService = notificationService;
        this.streamService = streamService;
    }

    /**
     * Liste des notifications. Sans paramètre : tout l'historique (la cloche a besoin du compteur exact).
     * Avec paramètres : le filtrage est fait par la base, et `limit` plafonne la réponse — la page affiche
     * alors une mention « résultats les plus récents » plutôt de laisser croire que rien d'autre n'existe.
     */
    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal User user,
                                          @RequestParam(required = false) @Pattern(regexp = "[A-Za-z_]{2,20}", message = "Type de notification inconnu.") String kind,
                                          @RequestParam(required = false) String state,
                                          @RequestParam(required = false) String since,
                                          @RequestParam(required = false) Integer limit) {
        boolean filtered = notBlank(kind) || notBlank(state) || notBlank(since) || limit != null;
        if (!filtered) {
            return notificationService.mine(user).stream().map(NotificationService::toRow).toList();
        }
        Boolean readFlag = switch (state == null ? "" : state.trim().toLowerCase()) {
            case "", "all" -> null;
            case "unread" -> false;
            case "read" -> true;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "État de lecture inconnu : all, unread ou read.");
        };
        Instant from = parseSince(since);
        int cap = limit == null ? DEFAULT_LIMIT : limit;
        if (cap < 1 || cap > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de notifications demandé : 1 à " + DEFAULT_LIMIT + ".");
        }
        return notificationService.mineFiltered(user, kind, from, readFlag, cap).stream()
                .map(NotificationService::toRow).toList();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Accepte « 2026-11-09 » (minuit UTC) ou un horodatage complet ; l'heure locale du poste est calculée par le navigateur. */
    private static Instant parseSince(String since) {
        if (!notBlank(since)) return null;
        String v = since.trim();
        try {
            return v.length() == 10 ? LocalDate.parse(v).atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.parse(v);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de départ illisible : " + v);
        }
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
