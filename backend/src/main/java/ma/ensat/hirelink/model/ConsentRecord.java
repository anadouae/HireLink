package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consent_records")
public class ConsentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 64)
    private String purpose;
    @Column(nullable = false)
    private boolean granted = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public void setGranted(boolean granted) { this.granted = granted; }
    public Long getUserId() { return userId; }
    public String getPurpose() { return purpose; }
    public boolean isGranted() { return granted; }
    public Instant getCreatedAt() { return createdAt; }
}
