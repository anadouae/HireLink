package ma.ensat.hirelink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class CvStorage {

    private static final int MAX_BYTES = 8_000_000;
    private final Path root;

    public CvStorage(@Value("${hirelink.cv.dir:data/cvs}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /**
     * Enregistre un CV. Le type est déterminé par le contenu (PDF, PNG, JPEG, WEBP, texte) ; le nom sur le disque est
     * un UUID généré par le serveur avec l'extension déduite du contenu : le nom envoyé par le client n'est jamais utilisé.
     */
    public String save(String folder, String originalName, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("CV trop lourd (8 Mo maximum)");
        }
        FileSignature.Kind kind = FileSignature.detect(bytes);
        if (kind == null || kind == FileSignature.GIF) {
            throw new IllegalArgumentException("Format de CV non accepté : PDF, PNG, JPG, WEBP ou texte (.txt).");
        }
        if (!folder.matches("[a-z]{3,16}")) throw new IllegalArgumentException("Dossier invalide");
        String relative = folder + "/" + UUID.randomUUID() + "." + kind.ext();
        Path dest = resolve(relative);
        Files.createDirectories(dest.getParent());
        Files.write(dest, bytes);
        return relative;
    }

    public void delete(String storageName) {
        try {
            Files.deleteIfExists(resolve(storageName));
        } catch (Exception ignored) {
            // effacement au mieux ; le fichier n'est plus référencé de toute façon
        }
    }

    public String mediaType(String storageName) {
        int dot = storageName == null ? -1 : storageName.lastIndexOf('.');
        return FileSignature.mimeForExt(dot < 0 ? "" : storageName.substring(dot + 1));
    }

    public String copy(String storageName, String folder) throws IOException {
        Path src = resolve(storageName);
        if (!Files.exists(src)) {
            throw new IllegalArgumentException("CV original introuvable");
        }
        return save(folder, null, Files.readAllBytes(src));
    }

    public Resource load(String storageName) {
        Path src = resolve(storageName);
        if (!Files.exists(src)) return null;
        return new FileSystemResource(src);
    }

    private Path resolve(String storageName) {
        if (storageName == null || storageName.isBlank() || storageName.contains("..")) {
            throw new IllegalArgumentException("Fichier CV invalide");
        }
        Path dest = root.resolve(storageName).normalize();
        if (!dest.startsWith(root)) {
            throw new IllegalArgumentException("Fichier CV invalide");
        }
        return dest;
    }
}
