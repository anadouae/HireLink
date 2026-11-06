package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "organization_notes", indexes = {
        @Index(name = "idx_org_notes_owner_updated", columnList = "organization_id,owner_user_id,updated_at")
})
public class OrganizationNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String title = "Post-it";

    @Column(length = 8000)
    private String content;

    @Column(name = "ink_color", nullable = false, length = 20)
    private String inkColor = "#243044";

    @Column(name = "note_color", nullable = false, length = 20)
    private String noteColor = "#fff4a8";

    @Column(name = "drawing_data", columnDefinition = "TEXT")
    private String drawingData;

    @Column(name = "position_x", nullable = false)
    private int positionX = 0;

    @Column(name = "position_y", nullable = false)
    private int positionY = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getInkColor() { return inkColor; }
    public void setInkColor(String inkColor) { this.inkColor = inkColor; }
    public String getNoteColor() { return noteColor; }
    public void setNoteColor(String noteColor) { this.noteColor = noteColor; }
    public String getDrawingData() { return drawingData; }
    public void setDrawingData(String drawingData) { this.drawingData = drawingData; }
    public int getPositionX() { return positionX; }
    public void setPositionX(int positionX) { this.positionX = positionX; }
    public int getPositionY() { return positionY; }
    public void setPositionY(int positionY) { this.positionY = positionY; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
