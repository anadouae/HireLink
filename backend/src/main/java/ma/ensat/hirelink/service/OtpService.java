package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.EmailOtp;
import ma.ensat.hirelink.repository.EmailOtpRepository;
import ma.ensat.hirelink.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.time.temporal.ChronoUnit;

@Service
public class OtpService {

    public static final String PURPOSE_REGISTER = "REGISTER";
    public static final String PURPOSE_LOGIN = "LOGIN";
    public static final String PURPOSE_REAUTH = "REAUTH";

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private final EmailOtpRepository otpRepository;
    private final MailService mail;
    private final SecureRandom random = new SecureRandom();
    private final byte[] hmacKey;
    public static final int MAX_ATTEMPTS = 5;

    @Value("${hirelink.otp.length:6}")
    private int length;

    @Value("${hirelink.otp.ttl-minutes:15}")
    private int ttlMinutes;

    @Value("${hirelink.otp.log-to-console:true}")
    private boolean logToConsole;

    public OtpService(EmailOtpRepository otpRepository, JwtService jwt, MailService mail) {
        this.otpRepository = otpRepository;
        this.mail = mail;
        this.hmacKey = jwt.deriveKey("otp-hmac");
    }

    /** Empreinte tronquée (64 bits) du code : la base ne contient jamais le code en clair. */
    private String digest(String email, String purpose, String code) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(hmacKey, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal((email + "|" + purpose + "|" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public String create(String email, String purpose) {
        String normalized = email.trim().toLowerCase();
        String code = generateCode();
        EmailOtp otp = new EmailOtp();
        otp.setEmail(normalized);
        otp.setPurpose(purpose);
        otp.setCode(digest(normalized, purpose, code));
        otp.setExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));
        otpRepository.save(otp);
        if (logToConsole) {
            log.info("════════════════════════════════════════");
            log.info(" HireLink OTP [{}] pour {} → {}", purpose, normalized, code);
            log.info("════════════════════════════════════════");
        }
        sendCode(normalized, purpose, code);
        return code;
    }

    private void sendCode(String email, String purpose, String code) {
        String heading = switch (purpose) {
            case PURPOSE_REGISTER -> "Votre code de vérification";
            case PURPOSE_LOGIN -> "Votre code de connexion";
            default -> "Votre code de confirmation";
        };
        String intro = PURPOSE_REGISTER.equals(purpose)
                ? "Bienvenue sur HireLink ! Saisissez ce code pour vérifier votre adresse e-mail et créer votre compte."
                : "Saisissez ce code pour confirmer votre identité.";
        EmailTemplates.Email message = EmailTemplates.Email.of(heading, intro,
                        "Ce code est valable " + ttlMinutes + " minutes et ne doit être communiqué à personne. "
                                + "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.")
                .withHighlight(code);
        mail.send(email, "HireLink — " + heading.toLowerCase(Locale.FRENCH), message, MailService.Kind.SECURITY, true, null);
    }

    /** Transaction indépendante : le compteur d'essais doit survivre à l'échec de l'appelant (A07). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean consume(String email, String purpose, String code) {
        String normalized = email.trim().toLowerCase();
        var found = otpRepository.findTopByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(normalized, purpose);
        if (found.isEmpty()) return false;
        EmailOtp o = found.get();
        if (o.getExpiresAt().isBefore(Instant.now()) || o.getAttempts() >= MAX_ATTEMPTS) {
            o.setConsumed(true);
            otpRepository.save(o);
            return false;
        }
        boolean ok = java.security.MessageDigest.isEqual(
                o.getCode().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                digest(normalized, purpose, code == null ? "" : code.trim()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (ok) {
            o.setConsumed(true);
        } else {
            o.setAttempts(o.getAttempts() + 1);
            if (o.getAttempts() >= MAX_ATTEMPTS) o.setConsumed(true);
        }
        otpRepository.save(o);
        return ok;
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, length);
        int n = random.nextInt(bound / 10, bound);
        return String.valueOf(n);
    }
}
