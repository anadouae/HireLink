package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Message envoyé depuis la page Support (signalement, demande de précision, question).
 * Visiteur ou utilisateur connecté : {@code userId} reste vide pour un visiteur.
 */
@Entity
@Table(name = "support_requests",
       indexes = {
           @Index(name = "idx_support_created", columnList = "created_at"),
           @Index(name = "idx_support_status", columnList = "status")
       })
public class SupportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SIGNALEMENT, QUESTION, COMPTE, ENTREPRISE, AUTRE. */
    @Column(nullable = false, length = 20)
    private String topic;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 160)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String message;

    /** Page ou élément concerné (ex. « offre 42 »), facultatif. */
    @Column(length = 300)
    private String reference;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_role", length = 20)
    private String userRole;

    /** NOUVEAU tant que personne n'a répondu, puis TRAITE. */
    @Column(nullable = false, length = 16)
    private String status = "NOUVEAU";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
