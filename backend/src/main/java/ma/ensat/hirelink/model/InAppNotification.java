package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/** F19 — notification in-app. */
@Entity
@Table(name = "notifications")
public class InAppNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 500)
    private String body;
    @Column(nullable = false, length = 48)
    private String kind = "INFO";
    @Column(nullable = false)
    private boolean readFlag = false;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
