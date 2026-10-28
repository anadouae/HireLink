package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.UserSession;
import ma.ensat.hirelink.repository.UserSessionRepository;
import ma.ensat.hirelink.security.ClientInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Sessions serveur par appareil.
 * <ul>
 *   <li>Jeton de rafraîchissement : 256 bits aléatoires, stocké uniquement sous forme d'empreinte SHA-256.</li>
 *   <li>Rotation à chaque rafraîchissement ; réutilisation d'un ancien jeton ⇒ session entière révoquée.</li>
 *   <li>Expiration d'inactivité (idle) et durée de vie absolue.</li>
 *   <li>Révocation : déconnexion, « tous les appareils », changement/réinitialisation de mot de passe, suspension, suppression.</li>
 * </ul>
 */
@Service
public class SessionService {

    public record Issued(UserSession session, String refreshToken) {}

    private final UserSessionRepository repository;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    private final Duration idle;
    private final Duration absolute;

    public SessionService(
            UserSessionRepository repository,
            AuditService audit,
            @Value("${hirelink.session.idle-minutes:120}") long idleMinutes,
            @Value("${hirelink.session.absolute-hours:168}") long absoluteHours
    ) {
        this.repository = repository;
        this.audit = audit;
        this.idle = Duration.ofMinutes(idleMinutes);
        this.absolute = Duration.ofHours(absoluteHours);
    }

    @Transactional
    public Issued create(User user, ClientInfo ci) {
        Instant now = Instant.now();
        String token = newToken();
        UserSession s = new UserSession();
        s.setId(UUID.randomUUID().toString());
        s.setUserId(user.getId());
        s.setTokenHash(sha256(token));
        s.setIp(ci.ip());
        s.setUserAgent(ci.userAgent());
        s.setDeviceLabel(deviceLabel(ci.userAgent()));
        s.setDeviceHash(sha256(deviceLabel(ci.userAgent())));
        s.setCreatedAt(now);
        s.setLastUsedAt(now);
        s.setIdleExpiresAt(now.plus(idle));
        s.setAbsoluteExpiresAt(now.plus(absolute));
        repository.save(s);
        return new Issued(s, token);
    }

    /** {@code true} si c'est le premier appareil de ce type vu pour l'utilisateur (alors qu'il a déjà eu des sessions). */
    @Transactional(readOnly = true)
    public boolean isNewDevice(User user, ClientInfo ci) {
        String hash = sha256(deviceLabel(ci.userAgent()));
        return repository.countByUserId(user.getId()) > 0 && !repository.existsByUserIdAndDeviceHash(user.getId(), hash);
    }

    /** Rotation du jeton de rafraîchissement. Lève 401 si invalide, expiré, révoqué ou réutilisé. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Issued rotate(String presented, ClientInfo ci) {
        if (presented == null || presented.length() < 30 || presented.length() > 100) throw unauthorized();
        String hash = sha256(presented);
        Instant now = Instant.now();
        UserSession s = repository.findByTokenHash(hash).orElse(null);
        if (s == null) {
            UserSession reused = repository.findByPreviousHash(hash).orElse(null);
            if (reused != null && reused.getRevokedAt() == null) {
                reused.setRevokedAt(now);
                reused.setRevokedReason("TOKEN_REUSE");
                repository.save(reused);
                audit.record("SESSION_TOKEN_REUSE", reused.getUserId(), null, "SESSION", reused.getId(), false,
                        "Ancien jeton de rafraîchissement réutilisé : session révoquée");
            }
            throw unauthorized();
        }
        if (!s.isActive(now)) throw unauthorized();
        String next = newToken();
        s.setPreviousHash(s.getTokenHash());
        s.setTokenHash(sha256(next));
        s.setLastUsedAt(now);
        s.setIdleExpiresAt(now.plus(idle));
        repository.save(s);
        return new Issued(s, next);
    }

    @Transactional
    public boolean revokeByRefreshToken(String presented, String reason) {
        return repository.findByTokenHash(sha256(presented)).filter(x -> x.getRevokedAt() == null).map(x -> {
            x.setRevokedAt(Instant.now());
            x.setRevokedReason(reason);
            repository.save(x);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String sessionId, Long userId) {
        if (sessionId == null) return false;
        return repository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId) && s.isActive(Instant.now()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<UserSession> active(Long userId) {
        Instant now = Instant.now();
        return repository.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(userId).stream()
                .filter(s -> s.isActive(now)).toList();
    }

    @Transactional
    public boolean revoke(String sessionId, Long userId, String reason) {
        return repository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId) && s.getRevokedAt() == null)
                .map(s -> {
                    s.setRevokedAt(Instant.now());
                    s.setRevokedReason(reason);
                    repository.save(s);
                    return true;
                }).orElse(false);
    }

    /** Révoque toutes les sessions de l'utilisateur, sauf {@code keepSessionId} (peut être null). Retourne le nombre révoqué. */
    @Transactional
    public int revokeAll(Long userId, String reason, String keepSessionId) {
        int n = 0;
        for (UserSession s : repository.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(userId)) {
            if (s.getId().equals(keepSessionId)) continue;
            s.setRevokedAt(Instant.now());
            s.setRevokedReason(reason);
            repository.save(s);
            n++;
        }
        return n;
    }

    public long absoluteSecondsLeft(UserSession s) {
        return Math.max(0, Duration.between(Instant.now(), s.getAbsoluteExpiresAt()).getSeconds());
    }

    @Scheduled(fixedDelay = 6 * 3600 * 1000L, initialDelay = 600_000L)
    @Transactional
    public void purge() {
        repository.purgeOlderThan(Instant.now().minus(Duration.ofDays(30)));
    }


    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée. Reconnectez-vous.");
    }

    private String newToken() {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Libellé lisible « Chrome · Windows » déduit du User-Agent (sans bibliothèque). */
    public static String deviceLabel(String ua) {
        String u = ua == null ? "" : ua;
        String browser = u.contains("Edg/") ? "Edge" : u.contains("OPR/") || u.contains("Opera") ? "Opera"
                : u.contains("Firefox/") ? "Firefox" : u.contains("Chrome/") ? "Chrome"
                : u.contains("Safari/") ? "Safari" : u.isBlank() ? "Navigateur" : "Navigateur";
        String os = u.contains("Windows") ? "Windows" : u.contains("Android") ? "Android"
                : u.contains("iPhone") || u.contains("iPad") ? "iOS" : u.contains("Mac OS") ? "macOS"
                : u.contains("Linux") ? "Linux" : "Système inconnu";
        return browser + " · " + os;
    }
}
