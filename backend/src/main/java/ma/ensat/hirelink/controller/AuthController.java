package ma.ensat.hirelink.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ma.ensat.hirelink.dto.AuthDtos;
import ma.ensat.hirelink.dto.AuthResponse;
import ma.ensat.hirelink.dto.AuthResult;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.security.ClientInfo;
import ma.ensat.hirelink.security.RefreshCookies;
import ma.ensat.hirelink.service.AuditService;
import ma.ensat.hirelink.service.AuthService;
import ma.ensat.hirelink.service.GoogleTokenService;
import ma.ensat.hirelink.service.PasswordResetService;
import ma.ensat.hirelink.service.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleTokenService googleTokenService;
    private final PasswordResetService passwordReset;
    private final RefreshCookies cookies;
    private final SessionService sessions;
    private final AuditService audit;

    @Value("${hirelink.google.client-id:}")
    private String googleClientId;

    public AuthController(AuthService authService, GoogleTokenService googleTokenService, PasswordResetService passwordReset,
                          RefreshCookies cookies, SessionService sessions, AuditService audit) {
        this.authService = authService;
        this.googleTokenService = googleTokenService;
        this.passwordReset = passwordReset;
        this.cookies = cookies;
        this.sessions = sessions;
        this.audit = audit;
    }

    private ResponseEntity<AuthResponse> respond(AuthResult r, HttpServletRequest req, HttpServletResponse res) {
        if (r.refreshToken() != null) cookies.write(res, req, r.refreshToken(), r.refreshMaxAgeSeconds());
        return ResponseEntity.ok(r.body());
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("googleClientId", googleClientId == null ? "" : googleClientId);
        m.put("googleConfigured", googleTokenService.isConfigured());
        m.put("googleDevBypass", googleTokenService.isDevBypass());
        m.put("frictionless", true);
        m.put("methods", new String[]{"email_otp", "password", "google", "passkey", "totp"});
        return m;
    }

    @PostMapping("/request-otp")
    public AuthDtos.Message requestOtp(@Valid @RequestBody AuthDtos.RequestOtp body, HttpServletRequest req) {
        authService.requestOtp(body.email(), body.purpose(), ClientInfo.from(req));
        return new AuthDtos.Message("Si l'adresse est valide, un code vient d'être envoyé (en démo locale : voir la console du serveur).");
    }

    @PostMapping("/verify-register-otp")
    public ResponseEntity<AuthResponse> verifyRegister(@Valid @RequestBody AuthDtos.VerifyRegisterOtp body,
                                                       HttpServletRequest req, HttpServletResponse res) {
        return respond(authService.verifyRegister(body, ClientInfo.from(req)), req, res);
    }

    @PostMapping("/verify-login-otp")
    public ResponseEntity<AuthResponse> verifyLoginOtp(@Valid @RequestBody AuthDtos.VerifyLoginOtp body,
                                                       HttpServletRequest req, HttpServletResponse res) {
        return respond(authService.verifyLoginOtp(body, ClientInfo.from(req)), req, res);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthDtos.PasswordLogin body,
                                              HttpServletRequest req, HttpServletResponse res) {
        return respond(authService.loginPassword(body, ClientInfo.from(req)), req, res);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody AuthDtos.GoogleLogin body,
                                               HttpServletRequest req, HttpServletResponse res) {
        return respond(authService.google(body, ClientInfo.from(req)), req, res);
    }

    // ---- double authentification à la connexion ----

    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> mfaVerify(@Valid @RequestBody AuthDtos.MfaVerify body,
                                                  HttpServletRequest req, HttpServletResponse res) {
        return respond(authService.mfaVerify(body, ClientInfo.from(req)), req, res);
    }

    @PostMapping("/mfa/enroll/start")
    public Map<String, Object> enrollStart(@Valid @RequestBody AuthDtos.MfaEnrollStart body) {
        return authService.enrollStart(body);
    }

    @PostMapping("/mfa/enroll/finish")
    public ResponseEntity<AuthResponse> enrollFinish(@Valid @RequestBody AuthDtos.MfaEnrollFinish body,
                                                     HttpServletRequest req, HttpServletResponse res) {
        return respond(authService.enrollFinish(body, ClientInfo.from(req)), req, res);
    }

    // ---- passkeys (connexion) ----

    @PostMapping("/passkey/login/options")
    public Map<String, Object> passkeyOptions(HttpServletRequest req) {
        return authService.passkeyLoginOptions(ClientInfo.from(req));
    }

    @PostMapping("/passkey/login/finish")
    public ResponseEntity<AuthResponse> passkeyFinish(@Valid @RequestBody AuthDtos.PasskeyFinish body,
                                                      HttpServletRequest req, HttpServletResponse res) {
        if (body.credential() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passkey manquante");
        return respond(authService.passkeyLoginFinish(body.requestId(), body.credential().toString(), ClientInfo.from(req)), req, res);
    }

    // ---- session ----

    /** Renouvelle le jeton d'accès avec le cookie HttpOnly ; le jeton de rafraîchissement est renouvelé (rotation). */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest req, HttpServletResponse res) {
        cookies.assertCsrf(req);
        String token = cookies.read(req);
        if (token == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée. Reconnectez-vous.");
        try {
            return respond(authService.refresh(token, ClientInfo.from(req)), req, res);
        } catch (ResponseStatusException e) {
            cookies.clear(res, req);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        cookies.assertCsrf(req);
        String token = cookies.read(req);
        if (token != null && sessions.revokeByRefreshToken(token, "LOGOUT")) {
            audit.record("LOGOUT", null, null, "SESSION", null, true, null);
        }
        cookies.clear(res, req);
        return ResponseEntity.noContent().build();
    }

    // ---- mot de passe oublié ----

    @PostMapping("/forgot-password")
    public AuthDtos.Message forgot(@Valid @RequestBody AuthDtos.ForgotPassword body, HttpServletRequest req) {
        passwordReset.request(body.email(), ClientInfo.from(req));
        return new AuthDtos.Message(PasswordResetService.GENERIC);
    }

    @PostMapping("/reset-password")
    public AuthDtos.Message reset(@Valid @RequestBody AuthDtos.ResetPassword body, HttpServletRequest req) {
        passwordReset.reset(body.token(), body.newPassword(), ClientInfo.from(req));
        return new AuthDtos.Message("Mot de passe modifié. Vous pouvez vous connecter.");
    }

    // ---- profil ----

    @PostMapping("/complete-profile")
    public AuthResponse completeProfile(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.CompleteProfile body) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
        return authService.completeProfile(user, body);
    }

    @PostMapping("/set-password")
    public AuthResponse setPassword(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.SetPassword body) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
        return authService.setPassword(user, body.password());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "mustSetPassword", user.isMustSetPassword(),
                "emailVerified", user.isEmailVerified(),
                "mfaEnabled", user.isMfaEnabled(),
                "skills", user.getSkills() == null ? "" : user.getSkills(),
                "company", user.getCompany() == null ? "" : user.getCompany()
        ));
    }
}
