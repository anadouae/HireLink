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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenService googleTokenService;
    private final CompanyVerificationService companyVerificationService;
    private final OrganizationService organizationService;

    public AuthService(
            UserRepository userRepository,
            OtpService otpService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            GoogleTokenService googleTokenService,
            CompanyVerificationService companyVerificationService,
            OrganizationService organizationService
    ) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenService = googleTokenService;
        this.companyVerificationService = companyVerificationService;
        this.organizationService = organizationService;
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
            user.setFullName(displayName(req));
            user.setRole(role);
            user.setPasswordHash(null);
            user.setMustSetPassword(true);
            user.setEmailVerified(true);
            user.setProfileComplete(false);
            applySignupProfile(user, req);
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

    private static final Set<String> MISSIONS = Set.of(
            "Stage", "Job étudiant", "Bénévolat", "Premier emploi", "CDI", "CDD"
    );

    @Transactional
    public AuthResponse completeProfile(User user, AuthDtos.CompleteProfile req) {
        if (req.role() != null && req.role() != Role.ADMINISTRATEUR) {
            user.setRole(req.role());
        }
        Role role = user.getRole();
        String firstRaw = req.firstName();
        String lastRaw = req.lastName();
        if ((firstRaw == null || firstRaw.isBlank()) && req.fullName() != null && !req.fullName().isBlank()) {
            String[] parts = req.fullName().strip().split("\\s+", 2);
            firstRaw = parts[0];
            if ((lastRaw == null || lastRaw.isBlank()) && parts.length > 1) lastRaw = parts[1];
        }
        String first = personName(firstRaw, "Prénom");
        String last = personName(lastRaw, "Nom");
        String phone = phoneInternational(req.phone());
        String city = requiredText(req.city(), "Ville", 2, 80);

        user.setFirstName(first);
        user.setLastName(last);
        user.setFullName(first + " " + last);
        user.setPhone(phone);
        if (req.phoneCountry() != null && !req.phoneCountry().isBlank()) {
            user.setPhoneCountry(req.phoneCountry().strip());
        }
        user.setCity(city);
        user.setPreferredLocations(city);

        if (role == Role.CANDIDAT) {
            String nationality = requiredText(req.nationality(), "Nationalité", 2, 80);
            user.setNationality(nationality);
            user.setBirthDate(birthDate(req.birthDate()));
            if (req.school() != null) user.setSchool(blankToNull(req.school()));
            if (req.educationLevel() != null && !req.educationLevel().isBlank()) user.setExperienceLevel(req.educationLevel().trim());
            if (req.workMode() != null && !req.workMode().isBlank()) user.setWorkMode(req.workMode().trim());
            if (req.metier() != null && !req.metier().isBlank()) user.setSoughtRoles(req.metier().trim());
            if (req.contracts() != null && !req.contracts().isBlank()) user.setPreferredContracts(cleanMissions(req.contracts()));
        } else if (role == Role.RECRUTEUR) {
            String company = requiredText(req.company(), "Entreprise", 2, 120);
            String job = requiredText(req.jobTitle(), "Fonction", 2, 120);
            String missions = cleanMissions(req.contracts());
            if (missions.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez au moins un type de mission");
            }
            user.setJobTitle(job);
            user.setSoughtRoles(job);
            user.setPreferredContracts(missions);
            String ice = req.companyIce() == null ? "" : req.companyIce().replaceAll("\\s", "");
            if (!ice.isBlank()) {
                applyCompanyIfRecruiter(user, Role.RECRUTEUR, company, ice);
            } else {
                user.setCompany(company);
            }
        }

        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < 6 || req.password().length() > 72) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe : 6 à 72 caractères");
            }
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            user.setMustSetPassword(false);
        } else if (user.isMustSetPassword() && (user.getPasswordHash() == null || user.getPasswordHash().isBlank())
                && (user.getGoogleSub() == null || user.getGoogleSub().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un mot de passe pour vous reconnecter");
        }
        user.setProfileComplete(true);
        userRepository.save(user);
        if (role == Role.RECRUTEUR) {
            organizationService.syncFromRecruiter(user);
        }
        return AuthResponse.of(jwtService.generate(user), user);
    }

    private static String personName(String value, String label) {
        String text = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " requis");
        }
        if (text.length() > 80) text = text.substring(0, 80).strip();
        if (!text.codePoints().anyMatch(Character::isLetter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " invalide");
        }
        return text;
    }

    private static String requiredText(String value, String label, int min, int max) {
        String text = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (text.length() < min) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " requis");
        }
        if (text.length() > max) text = text.substring(0, max).strip();
        return text;
    }

    private static String phoneInternational(String value) {
        String raw = value == null ? "" : value.strip();
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() < 8 || digits.length() > 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Numéro de téléphone invalide");
        }
        return "+" + digits;
    }

    private static LocalDate birthDate(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de naissance requise");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de naissance invalide");
        }
        LocalDate today = LocalDate.now();
        if (date.isAfter(today.minusYears(16)) || date.isBefore(today.minusYears(80))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de naissance hors plage (16 à 80 ans)");
        }
        return date;
    }

    private static String cleanMissions(String raw) {
        if (raw == null || raw.isBlank()) return "";
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String item = part.trim();
            if (MISSIONS.contains(item)) kept.add(item);
        }
        return String.join(", ", kept);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private String displayName(AuthDtos.VerifyRegisterOtp req) {
        if (req.fullName() != null && !req.fullName().isBlank()) return req.fullName().trim();
        String email = req.email() == null ? "" : req.email();
        return email.contains("@") ? email.split("@")[0] : "Compte";
    }

    private void applySignupProfile(User user, AuthDtos.VerifyRegisterOtp req) {
        if (req.phone() != null && !req.phone().isBlank()) user.setPhone(req.phone().trim());
        if (req.city() != null && !req.city().isBlank()) user.setPreferredLocations(req.city().trim());
        if (req.school() != null && !req.school().isBlank()) user.setSchool(req.school().trim());
        if (req.educationLevel() != null && !req.educationLevel().isBlank()) user.setExperienceLevel(req.educationLevel().trim());
        if (req.workMode() != null && !req.workMode().isBlank()) user.setWorkMode(req.workMode().trim());
        if (req.contracts() != null && !req.contracts().isBlank()) user.setPreferredContracts(req.contracts().trim());
        if (req.metier() != null && !req.metier().isBlank()) user.setSoughtRoles(req.metier().trim());
        if (req.jobTitle() != null && !req.jobTitle().isBlank()) {
            user.setJobTitle(req.jobTitle().trim());
            if (user.getSoughtRoles() == null || user.getSoughtRoles().isBlank()) user.setSoughtRoles(req.jobTitle().trim());
        }
    }

    private void applyCompanyIfRecruiter(User user, Role role, String company, String ice) {
        if (role != Role.RECRUTEUR) {
            return;
        }
        // Inscription libre : ICE + raison sociale + email — aucun document.
        // Badge / publication a posteriori (companyVerified).
        var result = companyVerificationService.verify(ice, company, user.getId(), user.getEmail());
        companyVerificationService.apply(user, result);
    }
}
