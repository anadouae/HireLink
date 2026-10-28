package ma.ensat.hirelink.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Jeton d'accès JWT (HS256) de courte durée.
 *
 * <ul>
 *   <li>Secret : variable d'environnement {@code HIRELINK_JWT_SECRET} (≥ 32 caractères). Sans elle, un secret
 *       aléatoire de 512 bits est généré au premier démarrage et conservé dans {@code data/jwt.key}
 *       (fichier ignoré par Git) : aucun secret n'est écrit dans le dépôt.</li>
 *   <li>Émetteur (iss), audience (aud), type (typ=access), identifiant (jti) et expiration sont contrôlés.</li>
 *   <li>Tolérance d'horloge : 60 s.</li>
 * </ul>
 */
@Service
public class JwtService {

    public static final String ISSUER = "hirelink-api";
    public static final String AUDIENCE = "hirelink-web";
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long accessTtlMs;

    public JwtService(
            @Value("${hirelink.jwt.secret:}") String configuredSecret,
            @Value("${hirelink.jwt.key-file:data/jwt.key}") String keyFile,
            @Value("${hirelink.jwt.access-ttl-minutes:15}") long accessTtlMinutes
    ) {
        String secret = configuredSecret == null ? "" : configuredSecret.trim();
        if (secret.isEmpty()) {
            secret = loadOrCreateKey(Path.of(keyFile));
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("HIRELINK_JWT_SECRET trop court : 32 caractères minimum.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = Math.max(1, accessTtlMinutes) * 60_000L;
    }

    private static String loadOrCreateKey(Path file) {
        try {
            if (Files.exists(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (existing.length() >= 43) return existing;
            }
            byte[] raw = new byte[64];
            new SecureRandom().nextBytes(raw);
            String generated = Base64.getEncoder().withoutPadding().encodeToString(raw);
            Path abs = file.toAbsolutePath();
            if (abs.getParent() != null) Files.createDirectories(abs.getParent());
            Files.writeString(abs, generated, StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(abs, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows : les droits NTFS du dossier utilisateur s'appliquent
            }
            log.info("Secret JWT généré dans {} (ne jamais le committer).", abs);
            return generated;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le secret JWT : " + e.getMessage(), e);
        }
    }

    public long accessTtlSeconds() {
        return accessTtlMs / 1000;
    }

    public String generate(User user, String sessionId) {
        Date now = new Date();
        return Jwts.builder()
                .claim("sid", sessionId)
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(String.valueOf(user.getId()))
                .claim("typ", "access")
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .notBefore(now)
                .expiration(new Date(now.getTime() + accessTtlMs))
                .signWith(key)
                .compact();
    }

    /** Jeton court à usage unique de flux (défi MFA, inscription MFA admin) : jamais accepté comme jeton d'accès. */
    public String generateFlowToken(Long userId, String type, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(String.valueOf(userId))
                .claim("typ", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** Retourne l'identifiant utilisateur d'un jeton de flux du type attendu, ou lève une exception. */
    public Long parseFlowToken(String token, String type) {
        Claims claims = Jwts.parser()
                .verifyWith(key).requireIssuer(ISSUER).requireAudience(AUDIENCE).clockSkewSeconds(60)
                .build().parseSignedClaims(token).getPayload();
        if (!type.equals(claims.get("typ", String.class))) throw new io.jsonwebtoken.JwtException("Type de jeton invalide");
        return Long.valueOf(claims.getSubject());
    }

    /** Clé dérivée (HMAC-SHA256 du secret et d'un libellé) : chiffrement des secrets TOTP, empreintes de codes… */
    public byte[] deriveKey(String purpose) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return mac.doFinal(("hirelink|" + purpose).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Vérifie signature, émetteur, audience, expiration et type ; lève une exception sinon. */
    public Claims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new io.jsonwebtoken.JwtException("Type de jeton invalide");
        }
        return claims;
    }

    public Long userId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public Role role(String token) {
        return Role.valueOf(parse(token).get("role", String.class));
    }
}
