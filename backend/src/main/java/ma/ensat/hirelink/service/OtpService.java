package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.EmailOtp;
import ma.ensat.hirelink.repository.EmailOtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class OtpService {

    public static final String PURPOSE_REGISTER = "REGISTER";
    public static final String PURPOSE_LOGIN = "LOGIN";

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private final EmailOtpRepository otpRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${hirelink.otp.length:6}")
    private int length;

    @Value("${hirelink.otp.ttl-minutes:15}")
    private int ttlMinutes;

    @Value("${hirelink.otp.log-to-console:true}")
    private boolean logToConsole;

    public OtpService(EmailOtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    @Transactional
    public String create(String email, String purpose) {
        String normalized = email.trim().toLowerCase();
        String code = generateCode();
        EmailOtp otp = new EmailOtp();
        otp.setEmail(normalized);
        otp.setPurpose(purpose);
        otp.setCode(code);
        otp.setExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));
        otpRepository.save(otp);
        if (logToConsole) {
            log.info("════════════════════════════════════════");
            log.info(" HireLink OTP [{}] pour {} → {}", purpose, normalized, code);
            log.info("════════════════════════════════════════");
        }
        return code;
    }

    @Transactional
    public boolean consume(String email, String purpose, String code) {
        String normalized = email.trim().toLowerCase();
        return otpRepository
                .findTopByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(normalized, purpose)
                .filter(o -> !o.getExpiresAt().isBefore(Instant.now()))
                .filter(o -> o.getCode().equals(code.trim()))
                .map(o -> {
                    o.setConsumed(true);
                    otpRepository.save(o);
                    return true;
                })
                .orElse(false);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, length);
        int n = random.nextInt(bound / 10, bound);
        return String.valueOf(n);
    }
}
