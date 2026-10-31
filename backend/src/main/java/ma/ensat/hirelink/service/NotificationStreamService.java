package ma.ensat.hirelink.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.ensat.hirelink.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifications en temps réel (Server-Sent Events).
 * Le navigateur ne peut pas envoyer d'en-tête Authorization avec EventSource et le jeton d'accès ne doit pas figurer dans une URL :
 * il demande donc un <b>ticket à usage unique valable 30 secondes</b> (requête authentifiée) et l'échange contre le flux.
 * Chaque flux est lié à une session : il est coupé dès que la session est révoquée (déconnexion, mot de passe changé,
 * appareil retiré) ou que le compte est désactivé, au plus tard au battement suivant.
 */
@Service
public class NotificationStreamService {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamService.class);
    private static final long TICKET_TTL_SECONDS = 30;
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int MAX_STREAMS_PER_USER = 8;

    private record Ticket(Long userId, String sessionId, Instant expiresAt) {}

    private record Stream(String sessionId, SseEmitter emitter) {}

    private final SessionService sessions;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<Long, List<Stream>> streams = new ConcurrentHashMap<>();

    public NotificationStreamService(SessionService sessions) {
        this.sessions = sessions;
    }

    public String issueTicket(User user, String sessionId) {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tickets.put(ticket, new Ticket(user.getId(), sessionId, Instant.now().plusSeconds(TICKET_TTL_SECONDS)));
        return ticket;
    }

    /** Échange un ticket (une seule fois) contre un flux lié à la session ; 401 si le ticket est inconnu, expiré ou la session révoquée. */
    public SseEmitter open(String ticket) {
        Ticket t = ticket == null ? null : tickets.remove(ticket);
        if (t == null || t.expiresAt().isBefore(Instant.now()) || !sessions.isActive(t.sessionId(), t.userId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ticket invalide ou expiré.");
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Stream stream = new Stream(t.sessionId(), emitter);
        List<Stream> mine = streams.computeIfAbsent(t.userId(), id -> new CopyOnWriteArrayList<>());
        mine.add(stream);
        while (mine.size() > MAX_STREAMS_PER_USER) close(t.userId(), mine.get(0));
        Runnable remove = () -> mine.remove(stream);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(t.userId(), stream, SseEmitter.event().name("ready").data("ok"));
        return emitter;
    }

    /** Envoie une notification à tous les onglets ouverts de cet utilisateur (et à lui seul). */
    public void publish(Long userId, Map<String, Object> notification) {
        List<Stream> mine = streams.get(userId);
        if (mine == null || mine.isEmpty()) return;
        String data;
        try {
            data = json.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            return;
        }
        for (Stream s : mine) send(userId, s, SseEmitter.event().name("notification").data(data));
    }

    /** Battement toutes les 15 s : maintient la connexion, vérifie la session, supprime les tickets expirés. */
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        tickets.values().removeIf(t -> t.expiresAt().isBefore(Instant.now()));
        streams.forEach((userId, mine) -> {
            for (Stream s : mine) {
                if (!sessions.isActive(s.sessionId(), userId)) close(userId, s);
                else send(userId, s, SseEmitter.event().comment("ping"));
            }
        });
        streams.values().removeIf(List::isEmpty);
    }

    private void send(Long userId, Stream stream, SseEmitter.SseEventBuilder event) {
        try {
            synchronized (stream) {
                stream.emitter().send(event);
            }
        } catch (IOException | IllegalStateException e) {
            close(userId, stream);
        }
    }

    private void close(Long userId, Stream stream) {
        List<Stream> mine = streams.get(userId);
        if (mine != null) mine.remove(stream);
        try {
            stream.emitter().complete();
        } catch (Exception e) {
            log.debug("Flux déjà fermé");
        }
    }
}
