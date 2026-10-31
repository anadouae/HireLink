package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.InAppNotification;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.InAppNotificationRepository;
import ma.ensat.hirelink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** F19 — notifications in-app, temps réel (SSE) et e-mail associé. */
@Service
public class NotificationService {
    private final InAppNotificationRepository repository;
    private final NotificationStreamService stream;
    private final MailService mail;
    private final UserRepository users;

    public NotificationService(InAppNotificationRepository repository, NotificationStreamService stream,
                               MailService mail, UserRepository users) {
        this.repository = repository;
        this.stream = stream;
        this.mail = mail;
        this.users = users;
    }

    /** Notification dans l'application, poussée en temps réel aux onglets ouverts une fois la transaction validée. */
    @Transactional
    public void push(Long userId, String title, String body, String kind) {
        InAppNotification n = new InAppNotification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setBody(body);
        n.setKind(kind == null ? "INFO" : kind);
        repository.save(n);
        Map<String, Object> row = toRow(n);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { stream.publish(userId, row); }
            });
        } else {
            stream.publish(userId, row);
        }
    }

    /** Même événement dans l'application et par e-mail (selon la préférence de l'utilisateur pour les e-mails de suivi). */
    @Transactional
    public void pushAndMail(Long userId, String title, String body, String kind, MailService.Kind mailKind,
                            String subject, EmailTemplates.Email email, MailService.Attachment attachment) {
        push(userId, title, body, kind);
        users.findById(userId).ifPresent(u -> mail.notify(u, "HireLink — " + subject, email, mailKind, attachment));
    }

    public static Map<String, Object> toRow(InAppNotification n) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n.getId());
        row.put("title", n.getTitle());
        row.put("body", n.getBody());
        row.put("kind", n.getKind());
        row.put("read", n.isReadFlag());
        row.put("at", n.getCreatedAt().toString());
        return row;
    }

    @Transactional(readOnly = true)
    public List<InAppNotification> mine(User user) {
        return repository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId());
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
