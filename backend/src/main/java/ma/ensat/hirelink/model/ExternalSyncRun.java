package ma.ensat.hirelink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Dernier compte rendu d'une synchronisation. Aucun secret ni contenu d'annonce. */
@Entity
@Table(name = "external_sync_runs")
public class ExternalSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "withdrawn_count", nullable = false)
    private int withdrawnCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "kept_previous", nullable = false)
    private boolean keptPrevious;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    public Long getId() { return id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }
    public int getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }
    public int getWithdrawnCount() { return withdrawnCount; }
    public void setWithdrawnCount(int withdrawnCount) { this.withdrawnCount = withdrawnCount; }
    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
    public boolean isKeptPrevious() { return keptPrevious; }
    public void setKeptPrevious(boolean keptPrevious) { this.keptPrevious = keptPrevious; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
