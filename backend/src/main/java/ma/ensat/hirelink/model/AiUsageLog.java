package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_usage_logs", indexes = {
        @Index(name = "idx_ai_user", columnList = "user_id"),
        @Index(name = "idx_ai_org", columnList = "organization_id"),
        @Index(name = "idx_ai_created", columnList = "created_at")
})
public class AiUsageLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "organization_id")
    private Long organizationId;
    @Column(nullable = false, length = 64)
    private String feature;
    @Column(nullable = false, length = 64)
    private String provider;
    @Column(length = 64)
    private String model;
    @Column(name = "chars_in")
    private int charsIn;
    @Column(name = "chars_out")
    private int charsOut;
    @Column(name = "credits_spent")
    private int creditsSpent;
    @Column(nullable = false)
    private boolean success;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public void setFeature(String feature) { this.feature = feature; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setModel(String model) { this.model = model; }
    public void setCharsIn(int charsIn) { this.charsIn = charsIn; }
    public void setCharsOut(int charsOut) { this.charsOut = charsOut; }
    public void setCreditsSpent(int creditsSpent) { this.creditsSpent = creditsSpent; }
    public void setSuccess(boolean success) { this.success = success; }
}
