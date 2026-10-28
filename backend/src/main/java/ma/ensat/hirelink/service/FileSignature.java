package ma.ensat.hirelink.service;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

/**
 * Reconnaissance du type réel d'un fichier par ses « magic bytes » (l'extension et le Content-Type envoyés par le
 * client ne sont jamais crus). Liste blanche stricte : PDF, PNG, JPEG, WEBP, GIF et texte brut UTF-8.
 */
public final class FileSignature {

    public record Kind(String ext, String mime) {}

    public static final Kind PDF = new Kind("pdf", "application/pdf");
    public static final Kind PNG = new Kind("png", "image/png");
    public static final Kind JPEG = new Kind("jpg", "image/jpeg");
    public static final Kind WEBP = new Kind("webp", "image/webp");
    public static final Kind GIF = new Kind("gif", "image/gif");
    public static final Kind TEXT = new Kind("txt", "text/plain; charset=UTF-8");

    private FileSignature() {}

    /** Retourne le type détecté, ou {@code null} si le contenu n'appartient pas à la liste blanche. */
    public static Kind detect(byte[] b) {
        if (b == null || b.length < 4) return null;
        if (startsWith(b, "%PDF-")) return PDF;
        if ((b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return PNG;
        if ((b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) return JPEG;
        if (startsWith(b, "GIF87a") || startsWith(b, "GIF89a")) return GIF;
        if (b.length > 12 && startsWith(b, "RIFF") && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return WEBP;
        if (looksLikeText(b)) return TEXT;
        return null;
    }

    /** Nom d'affichage assaini (lettres, chiffres, point, tiret, souligné, espace), 120 caractères maximum. */
    public static String safeName(String name) {
        if (name == null || name.isBlank()) return "cv";
        String n = name.strip().replaceAll("[^\\p{L}\\p{N}._ -]", "_").replaceAll("\\.{2,}", "_");
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    public static String mimeForExt(String ext) {
        return switch (ext == null ? "" : ext) {
            case "pdf" -> PDF.mime();
            case "png" -> PNG.mime();
            case "jpg" -> JPEG.mime();
            case "webp" -> WEBP.mime();
            case "gif" -> GIF.mime();
            case "txt" -> TEXT.mime();
            default -> "application/octet-stream";
        };
    }

    private static boolean looksLikeText(byte[] b) {
        int n = Math.min(b.length, 8192);
        for (int i = 0; i < n; i++) {
            int c = b[i] & 0xff;
            if (c == 0 || (c < 9) || (c > 13 && c < 32 && c != 27)) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b, 0, n));
            return true;
        } catch (CharacterCodingException e) {
            // coupure possible au milieu d'un caractère multi-octets en fin de tampon
            if (n < b.length) return true;
            return false;
        }
    }

    private static boolean startsWith(byte[] b, String prefix) {
        if (b.length < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (b[i] != (byte) prefix.charAt(i)) return false;
        }
        return true;
    }
}
