package ma.ensat.hirelink.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ma.ensat.hirelink.dto.AuthDtos;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.security.RefreshCookies;
import ma.ensat.hirelink.service.AccountService;
import ma.ensat.hirelink.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Sécurité du compte (utilisateur connecté, tous rôles). Les actions sensibles demandent une ré-authentification. */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService account;
    private final RefreshCookies cookies;

    public AccountController(AccountService account, RefreshCookies cookies) {
        this.account = account;
        this.cookies = cookies;
    }

    @GetMapping("/security")
    public Map<String, Object> security(@AuthenticationPrincipal User user) {
        return account.summary(user);
    }

    @PostMapping("/otp")
    public AuthDtos.Message reauthOtp(@AuthenticationPrincipal User user) {
        account.requestReauthOtp(user);
        return new AuthDtos.Message("Code envoyé (en démo locale : voir la console du serveur).");
    }

    @PostMapping("/password")
    public AuthDtos.Message changePassword(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.ChangePassword body) {
        account.changePassword(user, body, AuthService.currentSessionId());
        return new AuthDtos.Message("Mot de passe modifié. Vos autres appareils ont été déconnectés.");
    }

    // ---- sessions ----

    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions(@AuthenticationPrincipal User user) {
        return account.listSessions(user, AuthService.currentSessionId());
    }

    @DeleteMapping("/sessions/{id}")
    public Map<String, Object> revoke(@AuthenticationPrincipal User user, @PathVariable String id) {
        account.revokeSession(user, id);
        return Map.of("status", "REVOKED");
    }

    @PostMapping("/sessions/revoke-others")
    public Map<String, Object> revokeOthers(@AuthenticationPrincipal User user) {
        return Map.of("revoked", account.revokeOthers(user, AuthService.currentSessionId()));
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Map<String, Object>> revokeAll(@AuthenticationPrincipal User user, HttpServletRequest req, HttpServletResponse res) {
        int n = account.revokeAll(user);
        cookies.clear(res, req);
        return ResponseEntity.ok(Map.of("revoked", n));
    }

    // ---- double authentification ----

    @PostMapping("/mfa/setup")
    public Map<String, Object> mfaSetup(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.Reauth body) {
        return account.mfaSetup(user, body);
    }

    @PostMapping("/mfa/enable")
    public Map<String, Object> mfaEnable(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.MfaCode body) {
        return Map.of("backupCodes", account.mfaEnable(user, body.code()));
    }

    @PostMapping("/mfa/disable")
    public AuthDtos.Message mfaDisable(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.MfaCode body) {
        account.mfaDisable(user, body);
        return new AuthDtos.Message("Double authentification désactivée.");
    }

    @PostMapping("/mfa/backup-codes")
    public Map<String, Object> backupCodes(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.MfaCode body) {
        return Map.of("backupCodes", account.regenerateBackupCodes(user, body));
    }

    // ---- passkeys ----

    @PostMapping("/passkeys/options")
    public Map<String, Object> passkeyOptions(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.PasskeyRegisterStart body) {
        return account.passkeyStart(user, body);
    }

    @PostMapping("/passkeys")
    public AuthDtos.Message passkeyAdd(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.PasskeyFinish body) {
        account.passkeyFinish(user, body);
        return new AuthDtos.Message("Passkey ajoutée.");
    }

    @PostMapping("/passkeys/{id}/remove")
    public AuthDtos.Message passkeyRemove(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody AuthDtos.Reauth body) {
        account.passkeyRemove(user, id, body);
        return new AuthDtos.Message("Passkey supprimée.");
    }

    // ---- désactivation / effacement ----

    @PostMapping("/deactivate")
    public ResponseEntity<AuthDtos.Message> deactivate(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.Reauth body,
                                                       HttpServletRequest req, HttpServletResponse res) {
        account.deactivate(user, body);
        cookies.clear(res, req);
        return ResponseEntity.ok(new AuthDtos.Message("Compte désactivé."));
    }

    @PostMapping("/erase")
    public ResponseEntity<AuthDtos.Message> erase(@AuthenticationPrincipal User user, @Valid @RequestBody AuthDtos.Reauth body,
                                                  HttpServletRequest req, HttpServletResponse res) {
        account.erase(user, body);
        cookies.clear(res, req);
        return ResponseEntity.ok(new AuthDtos.Message("Vos données personnelles ont été effacées."));
    }
}
