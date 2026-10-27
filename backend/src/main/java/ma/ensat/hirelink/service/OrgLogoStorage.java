package ma.ensat.hirelink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OrgLogoStorage {

    private static final int MAX_BYTES = 2_000_000;
    private static final Set<String> ALLOWED = Set.of("png", "jpg", "jpeg", "webp", "gif", "svg");
    private final Path root;

    public OrgLogoStorage(@Value("${hirelink.logo.dir:data/org-logos}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String save(Long orgId, String originalName, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Logo trop lourd (2 Mo maximum)");
        }
        String ext = extension(originalName);
        if (!ALLOWED.contains(ext)) {
            throw new IllegalArgumentException("Formats acceptés : PNG, JPG, WEBP, GIF, SVG");
        }
        String relative = "org-" + orgId + "/" + UUID.randomUUID() + "." + ext;
        Path dest = resolve(relative);
        Files.createDirectories(dest.getParent());
        Files.write(dest, bytes);
        return relative;
    }

    public Resource load(String storageName) {
        Path src = resolve(storageName);
        if (!Files.exists(src)) return null;
        return new FileSystemResource(src);
    }

    public String mediaType(String storageName) {
        String ext = extension(storageName);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private Path resolve(String storageName) {
        if (storageName == null || storageName.isBlank() || storageName.contains("..")) {
            throw new IllegalArgumentException("Logo invalide");
        }
        Path dest = root.resolve(storageName).normalize();
        if (!dest.startsWith(root)) {
            throw new IllegalArgumentException("Logo invalide");
        }
        return dest;
    }

    private static String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
