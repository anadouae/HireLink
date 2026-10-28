package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.AuthDtos;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.UserSession;
import ma.ensat.hirelink.model.WebAuthnCredential;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.AuthThrottle;
import ma.ensat.hirelink.security.PasswordPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/**
 * Sécurité du compte de l'utilisateur connecté : mot de passe, sessions, MFA, passkeys, désactivation, effacement.
 * Toute action sensible exige une ré-authentification (mot de passe, ou code e-mail pour un compte sans mot de passe,
 * + code TOTP si la MFA est active).
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final AuthService auth;
    private final AuditService audit;
    private final AuthThrottle throttle;
    private final TotpService totp;
    private final OtpService otp;
    private final WebAuthnService webAuthn;
    private final PrivacyService privacy;
    private final MailService mail;
    private final NotificationService notifications;

    public AccountService(UserRepository users, PasswordEncoder encoder, SessionService sessions, AuthService auth,
                          AuditService audit, AuthThrottle throttle, TotpService totp, OtpService otp,
                          WebAuthnService webAuthn, PrivacyService privacy, MailService mail, NotificationService notifications) {
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.auth = auth;
        this.audit = audit;
        this.throttle = throttle;
        this.totp = totp;
        this.otp = otp;
        this.webAuthn = webAuthn;
        this.privacy = privacy;
        this.mail = mail;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------ ré-authentification

    /** Confirme l'identité de l'utilisateur avant une action sensible. Lève 403 (sans déconnecter) si refusée. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void reauth(User user, String currentPassword, String mfaCode, String otpCode, boolean requireMfa, String action) {
        String key = "reauth:" + user.getId();
        throttle.assertAllowed(key, 5, 900, "Trop de tentatives de confirmation. Réessayez dans quelques minutes.");
        boolean ok;
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        if (hasPassword) {
            ok = currentPassword != null && encoder.matches(currentPassword, user.getPasswordHash());
        } else {
            ok = otpCode != null && otp.consume(user.getEmail(), OtpService.PURPOSE_REAUTH, otpCode);
        }
        if (ok && requireMfa && user.isMfaEnabled()) {
            ok = mfaCode != null && auth.verifyMfaCode(user, mfaCode);
        }
        if (!ok) {
            throttle.record(key, 900);
            audit.log("REAUTH_FAILURE", user, "USER", user.getId(), false, action);
            String hint = hasPassword ? "Mot de passe actuel incorrect" : "Code de confirmation incorrect";
            if (hasPassword && user.isMfaEnabled() && requireMfa) hint += " (ou code de vérification invalide)";
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, hint + ".");
        }
        throttle.reset(key);
    }

    public void requestReauthOtp(User user) {
        throttle.hit("otp:email:" + user.getEmail(), 5, 600, "Trop de codes demandés. Réessayez dans quelques minutes.");
        otp.create(user.getEmail(), OtpService.PURPOSE_REAUTH);
    }

    // ------------------------------------------------------------ résumé

    @Transactional(readOnly = true)
    public Map<String, Object> summary(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", user.getEmail());
        m.put("role", user.getRole().name());
        m.put("hasPassword", user.getPasswordHash() != null && !user.getPasswordHash().isBlank());
        m.put("passwordChangedAt", user.getPasswordChangedAt() == null ? null : user.getPasswordChangedAt().toString());
        m.put("mfaEnabled", user.isMfaEnabled());
        m.put("mfaMandatory", user.getRole() == Role.ADMINISTRATEUR);
        m.put("backupCodesLeft", user.getMfaBackupHashes() == null || user.getMfaBackupHashes().isBlank() ? 0 : user.getMfaBackupHashes().split(",").length);
        m.put("passwordRules", PasswordPolicy.RULES);
        List<Map<String, Object>> keys = new ArrayList<>();
        for (WebAuthnCredential c : webAuthn.list(user.getId())) {
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("id", c.getId());
            k.put("name", c.getName());
            k.put("createdAt", c.getCreatedAt().toString());
            k.put("lastUsedAt", c.getLastUsedAt() == null ? null : c.getLastUsedAt().toString());
            keys.add(k);
        }
        m.put("passkeys", keys);
        return m;
    }

    // ------------------------------------------------------------ mot de passe

    @Transactional
    public void changePassword(User user, AuthDtos.ChangePassword req, String currentSid) {
        reauth(user, req.currentPassword(), req.mfaCode(), req.otp(), true, "Changement de mot de passe");
        PasswordPolicy.validate(req.newPassword(), user.getEmail(), user.getFullName());
        if (user.getPasswordHash() != null && encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setMustSetPassword(false);
        user.setPasswordChangedAt(Instant.now());
        users.save(user);
        int n = sessions.revokeAll(user.getId(), "PASSWORD_CHANGE", currentSid);
        audit.ok("PASSWORD_CHANGE", user, "USER", user.getId(), n + " autre(s) session(s) révoquée(s)");
        notifications.push(user.getId(), "Mot de passe modifié", "Votre mot de passe a été modifié. Vos autres appareils ont été déconnectés.", "SYSTEM");
        mail.send(user.getEmail(), "HireLink — votre mot de passe a été modifié",
                "Votre mot de passe HireLink vient d'être modifié. Si ce n'était pas vous, contactez immédiatement le support.", false);
    }

    // ------------------------------------------------------------ sessions

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSessions(User user, String currentSid) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserSession s : sessions.active(user.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("device", s.getDeviceLabel());
            m.put("ip", s.getIp());
            m.put("createdAt", s.getCreatedAt().toString());
            m.put("lastUsedAt", s.getLastUsedAt().toString());
            m.put("expiresAt", s.getIdleExpiresAt().isBefore(s.getAbsoluteExpiresAt()) ? s.getIdleExpiresAt().toString() : s.getAbsoluteExpiresAt().toString());
            m.put("current", s.getId().equals(currentSid));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public void revokeSession(User user, String sessionId) {
        if (!sessions.revoke(sessionId, user.getId(), "USER_REVOKED")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable");
        }
        audit.ok("SESSION_REVOKED", user, "SESSION", sessionId, null);
    }

    @Transactional
    public int revokeOthers(User user, String currentSid) {
        int n = sessions.revokeAll(user.getId(), "USER_REVOKED_OTHERS", currentSid);
        audit.ok("LOGOUT_ALL", user, "USER", user.getId(), n + " autre(s) session(s)");
        return n;
    }

    @Transactional
    public int revokeAll(User user) {
        int n = sessions.revokeAll(user.getId(), "LOGOUT_ALL", null);
        audit.ok("LOGOUT_ALL", user, "USER", user.getId(), n + " session(s), appareil courant inclus");
        return n;
    }

    // ------------------------------------------------------------ MFA

    @Transactional
    public Map<String, Object> mfaSetup(User user, AuthDtos.Reauth req) {
        reauth(user, req.currentPassword(), req.mfaCode(), req.otp(), false, "Activation de la double authentification");
        if (user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La double authentification est déjà active.");
        String secret = totp.newSecret();
        user.setMfaSecretEnc(totp.encrypt(secret));
        users.save(user);
        String uri = totp.otpauthUri("HireLink", user.getEmail(), secret);
        return Map.of("secret", secret, "otpauthUri", uri, "qr", totp.qrDataUri(uri));
    }

    @Transactional
    public List<String> mfaEnable(User user, String code) {
        if (user.isMfaEnabled() || user.getMfaSecretEnc() == null || !totp.verify(totp.decrypt(user.getMfaSecretEnc()), code, user.getId())) {
            audit.log("MFA_FAILURE", user, "USER", user.getId(), false, "Code d'activation invalide");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code invalide. Vérifiez l'heure de votre téléphone et réessayez.");
        }
        List<String> codes = auth.enableMfa(user);
        int n = sessions.revokeAll(user.getId(), "MFA_CHANGE", AuthService.currentSessionId());
        notifications.push(user.getId(), "Double authentification activée", "Votre compte est maintenant protégé par un code à usage unique.", "SYSTEM");
        return codes;
    }

    @Transactional
    public void mfaDisable(User user, AuthDtos.MfaCode req) {
        if (user.getRole() == Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La double authentification est obligatoire pour l'administrateur.");
        }
        if (!user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La double authentification n'est pas active.");
        reauth(user, req.currentPassword(), req.code(), req.otp(), true, "Désactivation de la double authentification");
        user.setMfaEnabled(false);
        user.setMfaSecretEnc(null);
        user.setMfaBackupHashes(null);
        users.save(user);
        sessions.revokeAll(user.getId(), "MFA_CHANGE", AuthService.currentSessionId());
        audit.ok("MFA_DISABLED", user, "USER", user.getId(), null);
        notifications.push(user.getId(), "Double authentification désactivée", "La double authentification vient d'être désactivée sur votre compte.", "SYSTEM");
    }

    @Transactional
    public List<String> regenerateBackupCodes(User user, AuthDtos.MfaCode req) {
        if (!user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La double authentification n'est pas active.");
        reauth(user, req.currentPassword(), req.code(), req.otp(), true, "Nouveaux codes de secours");
        List<String> codes = totp.newBackupCodes();
        user.setMfaBackupHashes(String.join(",", codes.stream().map(totp::hashBackup).toList()));
        users.save(user);
        audit.ok("MFA_BACKUP_CODES_REGENERATED", user, "USER", user.getId(), null);
        return codes;
    }

    // ------------------------------------------------------------ passkeys

    public Map<String, Object> passkeyStart(User user, AuthDtos.PasskeyRegisterStart req) {
        reauth(user, req.currentPassword(), req.mfaCode(), req.otp(), true, "Ajout d'une passkey");
        return webAuthn.startRegistration(user);
    }

    @Transactional
    public void passkeyFinish(User user, AuthDtos.PasskeyFinish req) {
        if (req.credential() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passkey manquante");
        WebAuthnCredential c = webAuthn.finishRegistration(user, req.requestId(), req.credential().toString(), req.name());
        audit.ok("PASSKEY_ADDED", user, "PASSKEY", c.getId(), c.getName());
        notifications.push(user.getId(), "Passkey ajoutée", "Une passkey « " + c.getName() + " » a été ajoutée à votre compte.", "SYSTEM");
    }

    @Transactional
    public void passkeyRemove(User user, Long id, AuthDtos.Reauth req) {
        reauth(user, req.currentPassword(), req.mfaCode(), req.otp(), true, "Suppression d'une passkey");
        if (!webAuthn.remove(user.getId(), id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passkey introuvable");
        audit.ok("PASSKEY_REMOVED", user, "PASSKEY", id, null);
    }

    // ------------------------------------------------------------ désactivation / effacement (RGPD)

    @Transactional
    public void deactivate(User user, AuthDtos.Reauth req) {
        if (user.getRole() == Role.ADMINISTRATEUR) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte administrateur ne peut pas être désactivé ici.");
        reauth(user, req.currentPassword(), req.mfaCode(), req.otp(), true, "Désactivation du compte");
        user.setActive(false);
        users.save(user);
        sessions.revokeAll(user.getId(), "ACCOUNT_DEACTIVATED", null);
        audit.ok("ACCOUNT_DEACTIVATED", user, "USER", user.getId(), null);
    }

    @Transactional
    public void erase(User user, AuthDtos.Reauth req) {
        if (user.getRole() == Role.ADMINISTRATEUR) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte administrateur ne peut pas être effacé ici.");
        reauth(user, req.currentPassword(), req.mfaCode(), req.otp(), true, "Effacement du compte");
        Long id = user.getId();
        String email = user.getEmail();
        privacy.erase(user);
        sessions.revokeAll(id, "ACCOUNT_ERASED", null);
        audit.record("ACCOUNT_ERASED", id, email, "USER", id, true, "Données personnelles anonymisées (loi 09-08 / RGPD)");
    }
}
