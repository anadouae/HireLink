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

    public String save(String folder, String originalName, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("CV trop lourd (8 Mo maximum)");
        }
        String safe = originalName == null ? "cv" : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 80) safe = safe.substring(safe.length() - 80);
        String relative = folder + "/" + UUID.randomUUID() + "_" + safe;
        Path dest = resolve(relative);
        Files.createDirectories(dest.getParent());
        Files.write(dest, bytes);
        return relative;
    }

    public String copy(String storageName, String folder) throws IOException {
        Path src = resolve(storageName);
        if (!Files.exists(src)) {
            throw new IllegalArgumentException("CV original introuvable");
        }
        return save(folder, src.getFileName().toString(), Files.readAllBytes(src));
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
