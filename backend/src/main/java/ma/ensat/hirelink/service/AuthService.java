package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.AuthDtos;
import ma.ensat.hirelink.dto.AuthResponse;
import ma.ensat.hirelink.dto.AuthResult;
import ma.ensat.hirelink.security.AuthThrottle;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.PasswordPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import ma.ensat.hirelink.model.ContractType;
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

    static final String GENERIC_LOGIN_ERROR =
            "Identifiants invalides. Vérifiez votre e-mail et votre mot de passe (ou utilisez le code e-mail, Google ou une passkey).";
    static final String LOCKED = "Trop de tentatives. Pour votre sécurité, réessayez dans quelques minutes.";
    private static final int ACCOUNT_MAX_FAILS = 5;
    private static final int IP_MAX_FAILS = 30;
    private static final long LOCK_WINDOW_SECONDS = 900;

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenService googleTokenService;
    private final CompanyVerificationService companyVerificationService;
    private final OrganizationService organizationService;
    private final SessionService sessionService;
    private final AuditService audit;
    private final AuthThrottle throttle;
    private final TotpService totp;
    private final NotificationService notifications;
    private final MailService mail;
    private final WebAuthnService webAuthn;
    private final EventNotifier notifier;
    private final String dummyHash;

    public AuthService(
            UserRepository userRepository,
            OtpService otpService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            GoogleTokenService googleTokenService,
            CompanyVerificationService companyVerificationService,
            OrganizationService organizationService,
            SessionService sessionService,
            AuditService audit,
            AuthThrottle throttle,
            TotpService totp,
            NotificationService notifications,
            MailService mail,
            WebAuthnService webAuthn,
            EventNotifier notifier
    ) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenService = googleTokenService;
        this.companyVerificationService = companyVerificationService;
        this.organizationService = organizationService;
        this.sessionService = sessionService;
        this.audit = audit;
        this.throttle = throttle;
        this.totp = totp;
        this.notifications = notifications;
        this.mail = mail;
        this.webAuthn = webAuthn;
        this.notifier = notifier;
        this.dummyHash = passwordEncoder.encode("hirelink-dummy-password-for-constant-time");
    }

    // ------------------------------------------------------------------ OTP e-mail

    public void requestOtp(String email, String purpose, ClientInfo ci) {
        String p = purpose.trim().toUpperCase();
        if (!OtpService.PURPOSE_REGISTER.equals(p) && !OtpService.PURPOSE_LOGIN.equals(p)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "purpose invalide (REGISTER|LOGIN)");
        }
        String normalized = email.trim().toLowerCase();
        throttle.hit("otp:email:" + normalized, 5, 600, "Trop de codes demandés pour cette adresse. Réessayez dans quelques minutes.");
        throttle.hit("otp:ip:" + ci.ip(), 20, 600, "Trop de codes demandés. Réessayez dans quelques minutes.");
        if (OtpService.PURPOSE_LOGIN.equals(p)) {
            User u = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
            if (u == null || !u.isActive()) {
                return; // réponse identique : aucune énumération de comptes
            }
        }
        otpService.create(normalized, p);
    }

    private void assertOtpAttemptsAllowed(ClientInfo ci) {
        throttle.assertAllowed("otpfail:ip:" + ci.ip(), 20, LOCK_WINDOW_SECONDS, LOCKED);
    }

    private ResponseStatusException otpInvalid(String email, ClientInfo ci) {
        throttle.record("otpfail:ip:" + ci.ip(), LOCK_WINDOW_SECONDS);
        audit.fail("OTP_FAILURE", email, "Code invalide ou expiré");
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code invalide ou expiré");
    }

    @Transactional
    public AuthResult verifyRegister(AuthDtos.VerifyRegisterOtp req, ClientInfo ci) {
        String email = req.email().trim().toLowerCase();
        assertOtpAttemptsAllowed(ci);
        if (!otpService.consume(email, OtpService.PURPOSE_REGISTER, req.code())) {
            throw otpInvalid(email, ci);
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
            audit.ok("REGISTER", user, "USER", user.getId(), "Rôle " + role);
            notifier.welcome(user);
        } else {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        return complete(user, "otp", ci);
    }

    @Transactional
    public AuthResult verifyLoginOtp(AuthDtos.VerifyLoginOtp req, ClientInfo ci) {
        String email = req.email().trim().toLowerCase();
        assertOtpAttemptsAllowed(ci);
        if (!otpService.consume(email, OtpService.PURPOSE_LOGIN, req.code())) {
            throw otpInvalid(email, ci);
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> otpInvalid(email, ci));
        return complete(user, "otp", ci);
    }

    // ------------------------------------------------------------------ mot de passe

    @Transactional
    public AuthResult loginPassword(AuthDtos.PasswordLogin req, ClientInfo ci) {
        String email = req.email().trim().toLowerCase();
        String acctKey = "login:acct:" + email;
        String ipKey = "login:ip:" + ci.ip();
        try {
            throttle.assertAllowed(acctKey, ACCOUNT_MAX_FAILS, LOCK_WINDOW_SECONDS, LOCKED);
            throttle.assertAllowed(ipKey, IP_MAX_FAILS, LOCK_WINDOW_SECONDS, LOCKED);
        } catch (ma.ensat.hirelink.security.TooManyRequestsException e) {
            audit.fail("LOGIN_LOCKED", email, "Verrouillage temporaire (trop d'échecs)");
            throw e;
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        String hash = user != null && user.getPasswordHash() != null && !user.getPasswordHash().isBlank() ? user.getPasswordHash() : null;
        boolean ok = passwordEncoder.matches(req.password(), hash != null ? hash : dummyHash) && hash != null;
        if (!ok) {
            throttle.record(acctKey, LOCK_WINDOW_SECONDS);
            throttle.record(ipKey, LOCK_WINDOW_SECONDS);
            audit.fail("LOGIN_FAILURE", email, "Identifiants invalides");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR);
        }
        throttle.reset(acctKey);
        if (passwordEncoder.upgradeEncoding(hash)) {
            user.setPasswordHash(passwordEncoder.encode(req.password())); // coût BCrypt relevé : on ré-empreinte à la volée
            userRepository.save(user);
        }
        return complete(user, "password", ci);
    }

    /** Définit le mot de passe de l'utilisateur déjà connecté (premier mot de passe) ; la session courante est conservée. */
    @Transactional
    public AuthResponse setPassword(User current, String password) {
        PasswordPolicy.validate(password, current.getEmail(), current.getFullName());
        current.setPasswordHash(passwordEncoder.encode(password));
        current.setMustSetPassword(false);
        current.setPasswordChangedAt(java.time.Instant.now());
        userRepository.save(current);
        audit.ok("PASSWORD_SET", current, "USER", current.getId(), null);
        return sameSession(current);
    }

    // ------------------------------------------------------------------ MFA (connexion)

    @Transactional
    public AuthResult mfaVerify(AuthDtos.MfaVerify req, ClientInfo ci) {
        Long uid = flowUser(req.mfaToken(), "mfa");
        String key = "mfa:" + uid;
        throttle.assertAllowed(key, ACCOUNT_MAX_FAILS, LOCK_WINDOW_SECONDS, LOCKED);
        User user = userRepository.findById(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session de connexion expirée"));
        if (!user.isActive() || !user.isMfaEnabled() || !verifyMfaCode(user, req.code())) {
            throttle.record(key, LOCK_WINDOW_SECONDS);
            audit.log("MFA_FAILURE", user, "USER", user.getId(), false, "Code de vérification invalide");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code invalide. Réessayez avec le code actuel de votre application.");
        }
        throttle.reset(key);
        return startSession(user, "password+totp", ci, null);
    }

    /** Étape 1 de l'enrôlement obligatoire (administrateur) : génère le secret TOTP et le QR code. */
    @Transactional
    public java.util.Map<String, Object> enrollStart(AuthDtos.MfaEnrollStart req) {
        Long uid = flowUser(req.enrollToken(), "mfa-enroll");
        User user = userRepository.findById(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session de connexion expirée"));
        if (user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La double authentification est déjà active.");
        String secret = totp.newSecret();
        user.setMfaSecretEnc(totp.encrypt(secret));
        userRepository.save(user);
        String uri = totp.otpauthUri("HireLink", user.getEmail(), secret);
        return java.util.Map.of("secret", secret, "otpauthUri", uri, "qr", totp.qrDataUri(uri), "email", user.getEmail());
    }

    @Transactional
    public AuthResult enrollFinish(AuthDtos.MfaEnrollFinish req, ClientInfo ci) {
        Long uid = flowUser(req.enrollToken(), "mfa-enroll");
        String key = "mfa:" + uid;
        throttle.assertAllowed(key, ACCOUNT_MAX_FAILS, LOCK_WINDOW_SECONDS, LOCKED);
        User user = userRepository.findById(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session de connexion expirée"));
        if (user.isMfaEnabled() || user.getMfaSecretEnc() == null || !totp.verify(totp.decrypt(user.getMfaSecretEnc()), req.code(), user.getId())) {
            throttle.record(key, LOCK_WINDOW_SECONDS);
            audit.log("MFA_FAILURE", user, "USER", user.getId(), false, "Code d'enrôlement invalide");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Code invalide. Vérifiez l'heure de votre téléphone et réessayez.");
        }
        throttle.reset(key);
        List<String> backups = enableMfa(user);
        return startSession(user, "password+totp", ci, backups);
    }

    /** Active la MFA (secret en attente déjà vérifié) et retourne les codes de secours en clair, à montrer une fois. */
    @Transactional
    public List<String> enableMfa(User user) {
        List<String> codes = totp.newBackupCodes();
        user.setMfaBackupHashes(String.join(",", codes.stream().map(totp::hashBackup).toList()));
        user.setMfaEnabled(true);
        userRepository.save(user);
        audit.ok("MFA_ENABLED", user, "USER", user.getId(), null);
        return codes;
    }

    /** Accepte un code TOTP ou un code de secours (à usage unique). */
    @Transactional
    public boolean verifyMfaCode(User user, String code) {
        if (user.getMfaSecretEnc() == null || code == null) return false;
        if (TotpService.looksLikeBackup(code)) {
            String h = totp.hashBackup(code);
            List<String> hashes = new java.util.ArrayList<>(java.util.Arrays.asList(
                    (user.getMfaBackupHashes() == null ? "" : user.getMfaBackupHashes()).split(",")));
            if (hashes.remove(h)) {
                user.setMfaBackupHashes(String.join(",", hashes));
                userRepository.save(user);
                audit.ok("MFA_BACKUP_CODE_USED", user, "USER", user.getId(), hashes.size() + " code(s) restant(s)");
                return true;
            }
            return false;
        }
        return totp.verify(totp.decrypt(user.getMfaSecretEnc()), code, user.getId());
    }

    private Long flowUser(String token, String type) {
        try {
            return jwtService.parseFlowToken(token, type);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Étape de connexion expirée. Recommencez la connexion.");
        }
    }

    // ------------------------------------------------------------------ passkeys

    public java.util.Map<String, Object> passkeyLoginOptions(ClientInfo ci) {
        throttle.hit("passkey:ip:" + ci.ip(), 60, 600, LOCKED);
        return webAuthn.startLogin();
    }

    @Transactional
    public AuthResult passkeyLoginFinish(String requestId, String credentialJson, ClientInfo ci) {
        throttle.assertAllowed("passkeyfail:ip:" + ci.ip(), 20, LOCK_WINDOW_SECONDS, LOCKED);
        User user;
        try {
            user = webAuthn.finishLogin(requestId, credentialJson);
        } catch (ResponseStatusException e) {
            throttle.record("passkeyfail:ip:" + ci.ip(), LOCK_WINDOW_SECONDS);
            audit.fail("LOGIN_FAILURE", null, "Passkey refusée");
            throw e;
        }
        return complete(user, "passkey", ci);
    }

    // ------------------------------------------------------------------ sessions

    /** Rafraîchit la session (rotation du jeton). */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResult refresh(String refreshToken, ClientInfo ci) {
        SessionService.Issued issued = sessionService.rotate(refreshToken, ci);
        User user = userRepository.findById(issued.session().getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            sessionService.revoke(issued.session().getId(), issued.session().getUserId(), "ACCOUNT_DISABLED");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée. Reconnectez-vous.");
        }
        AuthResponse body = AuthResponse.of(jwtService.generate(user, issued.session().getId()), jwtService.accessTtlSeconds(), user, null);
        return new AuthResult(body, issued.refreshToken(), sessionService.absoluteSecondsLeft(issued.session()));
    }

    /** Termine une connexion réussie : MFA si nécessaire, sinon ouverture de session serveur + jeton d'accès. */
    private AuthResult complete(User user, String method, ClientInfo ci) {
        if (!user.isActive()) {
            audit.log("LOGIN_FAILURE", user, "USER", user.getId(), false, "Compte désactivé");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce compte est désactivé. Contactez le support pour le réactiver.");
        }
        if (!"passkey".equals(method)) {
            if (user.isMfaEnabled()) {
                return AuthResult.withoutSession(AuthResponse.mfaChallenge(jwtService.generateFlowToken(user.getId(), "mfa", 300)));
            }
            if (user.getRole() == Role.ADMINISTRATEUR) {
                // L'administrateur ne se connecte JAMAIS avec le seul mot de passe : TOTP obligatoire (ou passkey).
                return AuthResult.withoutSession(AuthResponse.mfaEnroll(jwtService.generateFlowToken(user.getId(), "mfa-enroll", 600)));
            }
        }
        return startSession(user, method, ci, null);
    }

    private AuthResult startSession(User user, String method, ClientInfo ci, List<String> backupCodes) {
        boolean newDevice = sessionService.isNewDevice(user, ci);
        SessionService.Issued issued = sessionService.create(user, ci);
        audit.log("LOGIN_SUCCESS", user, "USER", user.getId(), true, "Méthode : " + method + " · " + issued.session().getDeviceLabel());
        if (newDevice) {
            notifications.push(user.getId(), "Nouvelle connexion",
                    "Connexion depuis un nouvel appareil : " + issued.session().getDeviceLabel() + ". Si ce n'était pas vous, révoquez la session dans Sécurité.", "SYSTEM");
            mail.send(user.getEmail(), "HireLink — nouvelle connexion",
                    "Une connexion vient d'avoir lieu depuis un nouvel appareil (" + issued.session().getDeviceLabel()
                            + "). Si ce n'était pas vous, changez votre mot de passe et révoquez la session depuis la page Sécurité.", false);
        }
        AuthResponse body = AuthResponse.of(jwtService.generate(user, issued.session().getId()), jwtService.accessTtlSeconds(), user, backupCodes);
        return new AuthResult(body, issued.refreshToken(), sessionService.absoluteSecondsLeft(issued.session()));
    }

    /** Nouveau jeton d'accès pour la session déjà ouverte (après modification de profil / mot de passe). */
    public AuthResponse sameSession(User user) {
        String sid = currentSessionId();
        return AuthResponse.of(jwtService.generate(user, sid), jwtService.accessTtlSeconds(), user, null);
    }

    public static String currentSessionId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof String sid ? sid : null;
    }

    @Transactional
    public AuthResponse completeProfile(User user, AuthDtos.CompleteProfile req) {
        if (req.role() != null && req.role() != Role.ADMINISTRATEUR && req.role() != user.getRole()) {
            if (user.isProfileComplete() || user.getRole() == Role.ADMINISTRATEUR) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le type de compte ne peut plus être modifié.");
            }
            audit.ok("ROLE_CHANGE", user, "USER", user.getId(), user.getRole() + " → " + req.role() + " (création du profil)");
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
            if (req.contracts() != null && !req.contracts().isBlank()) user.setPreferredContracts(ContractType.cleanPreferences(req.contracts()));
        } else if (role == Role.RECRUTEUR) {
            String company = requiredText(req.company(), "Entreprise", 2, 120);
            String job = requiredText(req.jobTitle(), "Fonction", 2, 120);
            String missions = ContractType.cleanPreferences(req.contracts());
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
            PasswordPolicy.validate(req.password(), user.getEmail(), first + " " + last);
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            user.setPasswordChangedAt(java.time.Instant.now());
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
        return sameSession(user);
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

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public AuthResult google(AuthDtos.GoogleLogin req, ClientInfo ci) {
        GoogleTokenService.GoogleIdentity identity;
        boolean devIdentity = false;
        if (req.idToken() != null && !req.idToken().isBlank()) {
            identity = googleTokenService.verifyIdToken(req.idToken());
        } else if (googleTokenService.isDevBypass()) {
            identity = googleTokenService.devIdentity(req.devEmail(), req.devName());
            devIdentity = true;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configurer hirelink.google.client-id");
        }

        User user = userRepository.findByGoogleSub(identity.sub()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(identity.email()).orElse(null);
            if (user != null && devIdentity) {
                // le mode démo ne doit jamais permettre de prendre la main sur un compte existant
                audit.fail("LOGIN_FAILURE", identity.email(), "google-dev sur compte existant refusé");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce compte existe déjà : utilisez votre mot de passe ou un code e-mail.");
            }
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
            notifier.welcome(user);
        } else {
            if (user.getGoogleSub() == null) {
                user.setGoogleSub(identity.sub());
            }
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        return complete(user, "google", ci);
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
