package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "catalog_for_you_run")
@IdClass(CatalogForYouRun.Pk.class)
public class CatalogForYouRun {

    @Id
    @Column(name = "run_id", length = 36, nullable = false)
    private String runId;

    @Id
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "match_score", nullable = false)
    private double matchScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CatalogForYouRun() {}

    public CatalogForYouRun(String runId, Long listingId, double matchScore) {
        this.runId = runId;
        this.listingId = listingId;
        this.matchScore = matchScore;
        this.createdAt = Instant.now();
    }

    public String getRunId() { return runId; }
    public Long getListingId() { return listingId; }
    public double getMatchScore() { return matchScore; }
    public Instant getCreatedAt() { return createdAt; }

    public static class Pk implements Serializable {
        private String runId;
        private Long listingId;
        public Pk() {}
        public Pk(String runId, Long listingId) { this.runId = runId; this.listingId = listingId; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(runId, pk.runId) && Objects.equals(listingId, pk.listingId);
        }
        @Override public int hashCode() { return Objects.hash(runId, listingId); }
    }
}