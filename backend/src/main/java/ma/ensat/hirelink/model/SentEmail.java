package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Boîte d'envoi : trace de chaque e-mail émis (ou seulement journalisé quand aucun serveur SMTP n'est configuré).
 * Le corps des messages contenant un code ou un lien secret n'est jamais conservé.
 */
@Entity
@Table(name = "sent_emails", indexes = {
        @Index(name = "idx_sent_emails_created", columnList = "created_at")
})
public class SentEmail {

    public enum Status { SENT, FAILED, LOGGED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 24)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(length = 200)
    private String error;

    @Column(name = "body_html", columnDefinition = "MEDIUMTEXT")
    private String bodyHtml;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getBodyHtml() { return bodyHtml; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public Instant getCreatedAt() { return createdAt; }
}
