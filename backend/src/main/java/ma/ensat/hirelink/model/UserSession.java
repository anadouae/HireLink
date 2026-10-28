package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Session serveur d'un appareil. Le jeton de rafraîchissement n'est jamais stocké en clair : seule son empreinte
 * SHA-256 l'est. {@code previousHash} permet de détecter la réutilisation d'un ancien jeton (vol probable) :
 * la session (la « famille » de jetons) est alors révoquée.
 */
@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_session_user", columnList = "user_id"),
        @Index(name = "idx_session_hash", columnList = "token_hash"),
        @Index(name = "idx_session_prev", columnList = "previous_hash")
})
public class UserSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 40)
    private String revokedReason;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Column(name = "device_hash", length = 64)
    private String deviceHash;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Instant getIdleExpiresAt() { return idleExpiresAt; }
    public void setIdleExpiresAt(Instant idleExpiresAt) { this.idleExpiresAt = idleExpiresAt; }
    public Instant getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public void setAbsoluteExpiresAt(Instant absoluteExpiresAt) { this.absoluteExpiresAt = absoluteExpiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getDeviceLabel() { return deviceLabel; }
    public void setDeviceLabel(String deviceLabel) { this.deviceLabel = deviceLabel; }
    public String getDeviceHash() { return deviceHash; }
    public void setDeviceHash(String deviceHash) { this.deviceHash = deviceHash; }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
    }
}
