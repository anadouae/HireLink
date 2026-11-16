package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.AuditLog;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.model.UserAvatar;
import ma.ensat.hirelink.repository.AuditLogRepository;
import ma.ensat.hirelink.repository.UserAvatarRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.AuthThrottle;
import ma.ensat.hirelink.security.ClientInfo;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profil du compte connecté. La photo reste en base, jamais dans un dossier public. */
@Service
public class AccountProfileService {

    private static final int MAX_BYTES = 1_500_000;

    private final UserRepository users;
    private final UserAvatarRepository avatars;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final OtpService otpService;
    private final AuthThrottle throttle;

    public AccountProfileService(UserRepository users, UserAvatarRepository avatars, AuditLogRepository auditLogs,
                                 PasswordEncoder encoder, AuditService audit, OtpService otpService,
                                 AuthThrottle throttle) {
        this.users = users;
        this.avatars = avatars;
        this.auditLogs = auditLogs;
        this.encoder = encoder;
        this.audit = audit;
        this.otpService = otpService;
        this.throttle = throttle;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(User user) {
        String full = user.getFullName() == null ? "" : user.getFullName().trim();
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = "";
        if (!first.isBlank() && full.startsWith(first)) {
            last = full.substring(first.length()).trim();
        } else if (first.isBlank() && full.contains(" ")) {
            int sp = full.indexOf(' ');
            first = full.substring(0, sp);
            last = full.substring(sp + 1).trim();
        } else if (first.isBlank()) {
            first = full;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firstName", first);
        m.put("lastName", last);
        m.put("fullName", full);
        m.put("email", user.getEmail());
        m.put("pendingEmail", user.getPendingEmail() == null ? "" : user.getPendingEmail());
        m.put("emailVerified", user.isEmailVerified());
        m.put("hasAvatar", avatars.existsById(user.getId()));
        return m;
    }

    @Transactional
    public Map<String, Object> update(User user, String firstName, String lastName, String email, String currentPassword) {
        String first = clean(firstName, 80);
        String last = clean(lastName, 80);
        if (first.isBlank() || last.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le prénom et le nom sont requis.");
        }
        String nextEmail = email == null ? user.getEmail() : email.trim().toLowerCase();
        boolean emailChangeRequested = false;
        if (!nextEmail.equalsIgnoreCase(user.getEmail())) {
            requestEmailChange(user, nextEmail, currentPassword);
            emailChangeRequested = true;
        }
        user.setFirstName(first);
        user.setLastName(last);
        user.setFullName(first + " " + last);
        users.save(user);
        audit.ok("PROFILE_UPDATED", user, "USER", user.getId(), emailChangeRequested ? "Profil + demande e-mail" : "Profil");
        Map<String, Object> out = profile(user);
        if (emailChangeRequested) {
            out.put("emailChangePending", true);
            out.put("message", "Un code a été envoyé à la nouvelle adresse. Confirmez-le pour activer le changement.");
        }
        return out;
    }

    /** Démarre un changement d’e-mail : mot de passe + OTP sur la nouvelle adresse (pas d’activation immédiate). */
    @Transactional
    public Map<String, Object> requestEmailChange(User user, String nextEmail, String currentPassword) {
        String ip = ClientInfo.current().ip();
        throttle.hit("email-change:user:" + user.getId(), 5, 3600, "Trop de demandes de changement d’e-mail. Réessayez plus tard.");
        throttle.hit("email-change:ip:" + ip, 15, 3600, "Trop de demandes de changement d’e-mail. Réessayez plus tard.");
        if (currentPassword == null || user.getPasswordHash() == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le mot de passe actuel est requis pour changer l'e-mail.");
        }
        String email = nextEmail == null ? "" : nextEmail.trim().toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail invalide.");
        }
        if (email.equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "C’est déjà votre e-mail actuel.");
        }
        users.findByEmailIgnoreCase(email).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet e-mail est déjà utilisé.");
            }
        });
        user.setPendingEmail(email);
        users.save(user);
        otpService.create(email, OtpService.PURPOSE_EMAIL_CHANGE);
        audit.ok("EMAIL_CHANGE_REQUESTED", user, "USER", user.getId(), email);
        return Map.of(
                "pendingEmail", email,
                "message", "Code envoyé à " + email + ". Confirmez pour finaliser le changement."
        );
    }

    @Transactional
    public Map<String, Object> confirmEmailChange(User user, String code) {
        String ip = ClientInfo.current().ip();
        throttle.hit("email-change-confirm:user:" + user.getId(), 12, 900, "Trop de tentatives. Réessayez dans quelques minutes.");
        throttle.hit("email-change-confirm:ip:" + ip, 20, 900, "Trop de tentatives. Réessayez dans quelques minutes.");
        String pending = user.getPendingEmail();
        if (pending == null || pending.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun changement d’e-mail en cours.");
        }
        if (code == null || code.isBlank() || !otpService.consume(pending, OtpService.PURPOSE_EMAIL_CHANGE, code.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code invalide ou expiré.");
        }
        users.findByEmailIgnoreCase(pending).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet e-mail est déjà utilisé.");
            }
        });
        String previous = user.getEmail();
        user.setEmail(pending);
        user.setPendingEmail(null);
        user.setEmailVerified(true);
        users.save(user);
        audit.ok("EMAIL_CHANGED", user, "USER", user.getId(), previous + " → " + pending);
        Map<String, Object> out = profile(user);
        out.put("message", "E-mail mis à jour et vérifié.");
        return out;
    }

    @Transactional
    public void saveAvatar(User user, byte[] data, String declared) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image trop lourde ou vide (1,5 Mo maximum).");
        }
        String type = sniff(data);
        if (type == null || (declared != null && !declared.isBlank() && !declared.equalsIgnoreCase(type))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formats acceptés : JPEG, PNG, WebP.");
        }
        UserAvatar avatar = avatars.findById(user.getId()).orElseGet(UserAvatar::new);
        avatar.setUserId(user.getId());
        avatar.setData(data);
        avatar.setContentType(type);
        avatar.setUpdatedAt(Instant.now());
        avatars.save(avatar);
        audit.ok("AVATAR_UPDATED", user, "USER", user.getId(), type);
    }

    @Transactional
    public void deleteAvatar(User user) {
        if (avatars.existsById(user.getId())) {
            avatars.deleteById(user.getId());
            audit.ok("AVATAR_REMOVED", user, "USER", user.getId(), null);
        }
    }

    @Transactional(readOnly = true)
    public UserAvatar avatar(User user) {
        return findAvatar(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune photo."));
    }

    /** Photo de profil si présente ; vide sinon (le contrôleur répond 204). */
    @Transactional(readOnly = true)
    public java.util.Optional<UserAvatar> findAvatar(User user) {
        return avatars.findById(user.getId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> logins(User user) {
        return auditLogs.findByActorUserIdAndActionInOrderByIdDesc(
                        user.getId(), List.of("LOGIN_SUCCESS", "LOGIN_FAILURE"), PageRequest.of(0, 20))
                .stream().map(this::loginRow).toList();
    }

    private Map<String, Object> loginRow(AuditLog row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", row.getCreatedAt().toString());
        m.put("result", row.isSuccess() ? "réussie" : "refusée");
        m.put("action", row.getAction());
        m.put("ip", row.getIp() == null ? "" : row.getIp());
        return m;
    }

    private static String clean(String raw, int max) {
        if (raw == null) return "";
        String v = raw.trim().replaceAll("\\s+", " ");
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String sniff(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return "image/png";
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return "image/webp";
        return null;
    }
}
