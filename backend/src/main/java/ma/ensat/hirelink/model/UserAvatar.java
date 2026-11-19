package ma.ensat.hirelink.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Photo de profil. Le fichier n'est pas déposé dans un dossier public : seule l'API authentifiée le sert. */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** MEDIUMBLOB (16 Mo) : un TINYBLOB (255 octets) ne contient aucune photo réelle. */
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
