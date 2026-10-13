package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.AuthDtos;
import ma.ensat.hirelink.dto.AuthResponse;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenService googleTokenService;
    private final CompanyVerificationService companyVerificationService;

    public AuthService(
            UserRepository userRepository,
            OtpService otpService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            GoogleTokenService googleTokenService,
            CompanyVerificationService companyVerificationService
    ) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenService = googleTokenService;
        this.companyVerificationService = companyVerificationService;
    }

    public void requestOtp(String email, String purpose) {
        String p = purpose.trim().toUpperCase();
        if (!OtpService.PURPOSE_REGISTER.equals(p) && !OtpService.PURPOSE_LOGIN.equals(p)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "purpose invalide (REGISTER|LOGIN)");
        }
        String normalized = email.trim().toLowerCase();
        if (OtpService.PURPOSE_LOGIN.equals(p)) {
            userRepository.findByEmailIgnoreCase(normalized)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable"));
        }
        otpService.create(normalized, p);
    }

    @Transactional
    public AuthResponse verifyRegister(AuthDtos.VerifyRegisterOtp req) {
        String email = req.email().trim().toLowerCase();
        if (!otpService.consume(email, OtpService.PURPOSE_REGISTER, req.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code OTP invalide ou expiré");
        }
        Role role = req.role() == null ? Role.CANDIDAT : req.role();
        if (role == Role.ADMINISTRATEUR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle admin non autorisé à l'inscription");
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFullName(req.fullName() == null || req.fullName().isBlank()
                    ? email.split("@")[0]
                    : req.fullName().trim());
            user.setRole(role);
            user.setPasswordHash(null);
            user.setMustSetPassword(true);
            user.setEmailVerified(true);
            user.setProfileComplete(false);
            applyCompanyIfRecruiter(user, role, req.company(), req.companyIce());
            userRepository.save(user);
        } else {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        return AuthResponse.of(jwtService.generate(user), user);
    }

    @Transactional
    public AuthResponse verifyLoginOtp(AuthDtos.VerifyLoginOtp req) {
        String email = req.email().trim().toLowerCase();
        if (!otpService.consume(email, OtpService.PURPOSE_LOGIN, req.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code OTP invalide ou expiré");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable"));
        return AuthResponse.of(jwtService.generate(user), user);
    }

    public AuthResponse loginPassword(AuthDtos.PasswordLogin req) {
        User user = userRepository.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides"));
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun mot de passe — utilisez le code email ou Google");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }
        return AuthResponse.of(jwtService.generate(user), user);
    }

    @Transactional
    public AuthResponse setPassword(User current, String password) {
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe trop court (min 6)");
        }
        current.setPasswordHash(passwordEncoder.encode(password));
        current.setMustSetPassword(false);
        userRepository.save(current);
        return AuthResponse.of(jwtService.generate(current), current);
    }

    @Transactional
    public AuthResponse google(AuthDtos.GoogleLogin req) {
        GoogleTokenService.GoogleIdentity identity;
        if (req.idToken() != null && !req.idToken().isBlank()) {
            identity = googleTokenService.verifyIdToken(req.idToken());
        } else if (googleTokenService.isDevBypass()) {
            identity = googleTokenService.devIdentity(req.devEmail(), req.devName());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configurer hirelink.google.client-id");
        }

        User user = userRepository.findByGoogleSub(identity.sub()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(identity.email()).orElse(null);
        }
        if (user == null) {
            Role role = req.role() == null ? Role.CANDIDAT : req.role();
            if (role == Role.ADMINISTRATEUR) {
                role = Role.CANDIDAT;
            }
            user = new User();
            user.setEmail(identity.email());
            user.setFullName(identity.name());
            user.setRole(role);
            user.setGoogleSub(identity.sub());
            user.setEmailVerified(identity.emailVerified());
            user.setMustSetPassword(true);
            user.setPasswordHash(null);
            user.setProfileComplete(false);
            applyCompanyIfRecruiter(user, role, req.company(), req.companyIce());
            userRepository.save(user);
        } else {
            if (user.getGoogleSub() == null) {
                user.setGoogleSub(identity.sub());
            }
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        return AuthResponse.of(jwtService.generate(user), user);
    }

    private void applyCompanyIfRecruiter(User user, Role role, String company, String ice) {
        if (role != Role.RECRUTEUR) {
            return;
        }
        // Inscription libre : ICE + raison sociale + email — aucun document.
        // Badge / publication a posteriori (companyVerified).
        var result = companyVerificationService.verify(ice, company, user.getId(), user.getEmail());
        user.setCompanyIce(result.ice());
        user.setCompany(result.companyName());
        user.setCompanyVerified(result.verified());
        user.setVerificationStatus(result.status());
    }
}
