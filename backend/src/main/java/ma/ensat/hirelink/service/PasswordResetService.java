package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.PasswordResetToken;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.PasswordResetTokenRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.AuthThrottle;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Réinitialisation du mot de passe : jeton à usage unique, haché, 30 minutes, réponse identique que le compte existe ou non. */
@Service
public class PasswordResetService {

    public static final String GENERIC = "Si un compte existe pour cette adresse, un lien de réinitialisation vient d'être envoyé (valable 30 minutes).";
    private static final long TTL_MINUTES = 30;

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final AuditService audit;
    private final MailService mail;
    private final NotificationService notifications;
    private final AuthThrottle throttle;
    private final SecureRandom random = new SecureRandom();

    @Value("${hirelink.frontend.base-url:http://localhost:5500}")
    private String frontendBase;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, PasswordEncoder encoder,
                                SessionService sessions, AuditService audit, MailService mail,
                                NotificationService notifications, AuthThrottle throttle) {
        this.tokens = tokens;
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.audit = audit;
        this.mail = mail;
        this.notifications = notifications;
        this.throttle = throttle;
    }

    @Transactional
    public void request(String emailRaw, ClientInfo ci) {
        String email = emailRaw.trim().toLowerCase();
        throttle.hit("forgot:email:" + email, 3, 3600, "Trop de demandes. Réessayez plus tard.");
        throttle.hit("forgot:ip:" + ci.ip(), 10, 3600, "Trop de demandes. Réessayez plus tard.");
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isActive()) {
            audit.fail("PASSWORD_RESET_REQUEST", email, "Compte inconnu ou désactivé (réponse générique envoyée)");
            return;
        }
        tokens.invalidateAllFor(user.getId(), Instant.now());
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(user.getId());
        t.setTokenHash(SessionService.sha256(token));
        t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(TTL_MINUTES)));
        tokens.save(t);
        String link = frontendBase.replaceAll("/$", "") + "/auth.html?mode=reset&token=" + token;
        mail.send(user.getEmail(), "HireLink — réinitialisation de votre mot de passe",
                "Bonjour,\n\nPour choisir un nouveau mot de passe, ouvrez ce lien (valable " + TTL_MINUTES + " minutes, usage unique) :\n"
                        + link + "\n\nSi vous n'êtes pas à l'origine de cette demande, ignorez ce message.", true);
        audit.ok("PASSWORD_RESET_REQUEST", user, "USER", user.getId(), null);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void reset(String token, String newPassword, ClientInfo ci) {
        throttle.hit("reset:ip:" + ci.ip(), 20, 900, "Trop de tentatives. Réessayez dans quelques minutes.");
        PasswordResetToken t = tokens.findByTokenHash(SessionService.sha256(token == null ? "" : token.trim())).orElse(null);
        if (t == null || t.getUsedAt() != null || t.getExpiresAt().isBefore(Instant.now())) {
            audit.fail("PASSWORD_RESET", null, "Jeton invalide, expiré ou déjà utilisé");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lien invalide ou expiré. Refaites une demande de réinitialisation.");
        }
        User user = users.findById(t.getUserId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lien invalide ou expiré."));
        PasswordPolicy.validate(newPassword, user.getEmail(), user.getFullName());
        t.setUsedAt(Instant.now());
        tokens.save(t);
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustSetPassword(false);
        user.setPasswordChangedAt(Instant.now());
        users.save(user);
        tokens.invalidateAllFor(user.getId(), Instant.now());
        int revoked = sessions.revokeAll(user.getId(), "PASSWORD_RESET", null);
        audit.ok("PASSWORD_RESET", user, "USER", user.getId(), revoked + " session(s) révoquée(s)");
        notifications.push(user.getId(), "Mot de passe modifié",
                "Votre mot de passe vient d'être réinitialisé et vos appareils ont été déconnectés. Si ce n'était pas vous, contactez le support.", "SYSTEM");
        mail.send(user.getEmail(), "HireLink — votre mot de passe a été modifié",
                "Votre mot de passe HireLink vient d'être modifié. Si ce n'était pas vous, contactez immédiatement le support.", false);
    }
}
