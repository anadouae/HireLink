package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Journal d'audit des actions sensibles. Ne contient jamais de mot de passe, jeton, code OTP ni secret. */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_at", columnList = "created_at"),
        @Index(name = "idx_audit_actor", columnList = "actor_user_id")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_email", length = 160)
    private String actorEmail;

    @Column(nullable = false, length = 48)
    private String action;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(length = 64)
    private String ip;

    @Column(nullable = false)
    private boolean success = true;

    @Column(length = 500)
    private String detail;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
