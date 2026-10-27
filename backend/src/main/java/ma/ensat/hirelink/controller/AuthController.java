package ma.ensat.hirelink.controller;

import jakarta.validation.Valid;
import ma.ensat.hirelink.dto.AuthDtos;
import ma.ensat.hirelink.dto.AuthResponse;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.service.AuthService;
import ma.ensat.hirelink.service.GoogleTokenService;
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

    @Value("${hirelink.google.client-id:}")
    private String googleClientId;

    public AuthController(AuthService authService, GoogleTokenService googleTokenService) {
        this.authService = authService;
        this.googleTokenService = googleTokenService;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("googleClientId", googleClientId == null ? "" : googleClientId);
        m.put("googleConfigured", googleTokenService.isConfigured());
        m.put("googleDevBypass", googleTokenService.isDevBypass());
        m.put("frictionless", true);
        m.put("methods", new String[]{"email_otp", "password", "google"});
        return m;
    }

    @PostMapping("/request-otp")
    public AuthDtos.Message requestOtp(@Valid @RequestBody AuthDtos.RequestOtp body) {
        authService.requestOtp(body.email(), body.purpose());
        return new AuthDtos.Message("Code envoyé (voir console backend en démo locale)");
    }

    @PostMapping("/verify-register-otp")
    public AuthResponse verifyRegister(@Valid @RequestBody AuthDtos.VerifyRegisterOtp body) {
        return authService.verifyRegister(body);
    }

    @PostMapping("/verify-login-otp")
    public AuthResponse verifyLoginOtp(@Valid @RequestBody AuthDtos.VerifyLoginOtp body) {
        return authService.verifyLoginOtp(body);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthDtos.PasswordLogin body) {
        return authService.loginPassword(body);
    }

    @PostMapping("/complete-profile")
    public AuthResponse completeProfile(
            @AuthenticationPrincipal User user,
            @RequestBody AuthDtos.CompleteProfile body
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
        }
        return authService.completeProfile(user, body);
    }

    @PostMapping("/set-password")
    public AuthResponse setPassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AuthDtos.SetPassword body
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
        }
        return authService.setPassword(user, body.password());
    }

    @PostMapping("/google")
    public AuthResponse google(@RequestBody AuthDtos.GoogleLogin body) {
        return authService.google(body);
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
                "skills", user.getSkills() == null ? "" : user.getSkills(),
                "company", user.getCompany() == null ? "" : user.getCompany()
        ));
    }
}
