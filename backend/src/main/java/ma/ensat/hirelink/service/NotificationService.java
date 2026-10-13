package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.InAppNotification;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.InAppNotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** F19 — notifications in-app (email optionnel via mail.enabled). */
@Service
public class NotificationService {
    private final InAppNotificationRepository repository;

    public NotificationService(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void push(Long userId, String title, String body, String kind) {
        InAppNotification n = new InAppNotification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setBody(body);
        n.setKind(kind == null ? "INFO" : kind);
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public List<InAppNotification> mine(User user) {
        return repository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public void markRead(User user, Long id) {
        InAppNotification n = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));
        if (!n.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        n.setReadFlag(true);
        repository.save(n);
    }

    @Transactional
    public InAppNotification create(User user, String title, String body, String kind) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title requis");
        }
        InAppNotification n = new InAppNotification();
        n.setUserId(user.getId());
        n.setTitle(title.trim());
        n.setBody(body == null ? "" : body.trim());
        n.setKind(kind == null || kind.isBlank() ? "INFO" : kind.trim());
        return repository.save(n);
    }

    @Transactional
    public void delete(User user, Long id) {
        InAppNotification n = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));
        if (!n.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        repository.delete(n);
    }
}
