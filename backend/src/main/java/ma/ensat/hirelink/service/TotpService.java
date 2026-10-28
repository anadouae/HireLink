package ma.ensat.hirelink.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import ma.ensat.hirelink.security.JwtService;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOTP RFC 6238 (HMAC-SHA1, 6 chiffres, pas de 30 s, tolérance ±1 pas) + codes de secours.
 * Le secret est chiffré au repos (AES-256-GCM, clé dérivée du secret serveur). Un code déjà utilisé est refusé (anti-rejeu).
 */
@Service
public class TotpService {

    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String BACKUP_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private final SecureRandom random = new SecureRandom();
    private final byte[] aesKey;
    private final byte[] backupKey;
    private final Map<Long, Long> lastStep = new ConcurrentHashMap<>();

    public TotpService(JwtService jwt) {
        this.aesKey = jwt.deriveKey("totp-secret-aes");
        this.backupKey = jwt.deriveKey("backup-code-hmac");
    }

    public String newSecret() {
        byte[] raw = new byte[20];
        random.nextBytes(raw);
        return base32(raw);
    }

    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String decrypt(String enc) {
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, 12)));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Déchiffrement impossible", e);
        }
    }

    /** Vérifie un code à 6 chiffres. {@code userId} sert à refuser le rejeu d'un code déjà accepté. */
    public boolean verify(String secret, String code, Long userId) {
        if (code == null || !code.trim().matches("\\d{6}")) return false;
        long now = System.currentTimeMillis() / 30_000L;
        for (long step = now - 1; step <= now + 1; step++) {
            if (constantEquals(generate(secret, step), code.trim())) {
                Long last = userId == null ? null : lastStep.get(userId);
                if (last != null && step <= last) return false;
                if (userId != null) lastStep.put(userId, step);
                return true;
            }
        }
        return false;
    }

    public String generate(String secret, long step) {
        try {
            byte[] key = unbase32(secret);
            byte[] msg = new byte[8];
            for (int i = 7, v = 0; i >= 0; i--) { msg[i] = (byte) ((step >> (8 * (7 - i))) & 0xff); }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int off = h[h.length - 1] & 0x0f;
            int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16) | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String otpauthUri(String issuer, String email, String secret) {
        String label = URLEncoder.encode(issuer + ":" + email, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    public String qrDataUri(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 240, 240,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(m, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    // ---- codes de secours ----

    /** Retourne 10 codes en clair (à montrer une seule fois) ; l'appelant stocke {@link #hashBackup}. */
    public List<String> newBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                if (j == 5) sb.append('-');
                sb.append(BACKUP_ALPHABET.charAt(random.nextInt(BACKUP_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    public String hashBackup(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(backupKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean looksLikeBackup(String code) {
        return code != null && code.trim().toLowerCase(Locale.ROOT).matches("[a-z0-9]{5}-[a-z0-9]{5}");
    }

    private static boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    private static byte[] unbase32(String s) {
        String in = s.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : in.toCharArray()) {
            int v = B32.indexOf(c);
            if (v < 0) continue;
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
