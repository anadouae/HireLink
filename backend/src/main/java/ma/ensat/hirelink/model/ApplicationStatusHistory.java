package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Timeline immuable F13 / R12 — append-only. */
@Entity
@Table(name = "application_status_history", indexes = {
        @Index(name = "idx_ash_application", columnList = "application_id"),
        @Index(name = "idx_ash_created", columnList = "created_at")
})
public class ApplicationStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "application_id", nullable = false)
    private Long applicationId;
    @Column(name = "from_status", length = 32)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
