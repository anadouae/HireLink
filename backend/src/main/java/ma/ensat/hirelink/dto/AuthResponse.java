package ma.ensat.hirelink.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ma.ensat.hirelink.model.Role;

import java.util.List;

/**
 * Réponse d'authentification. Le jeton de rafraîchissement n'est JAMAIS dans le corps : il voyage dans un cookie
 * HttpOnly. Si une double authentification est requise, {@code accessToken} est absent et {@code mfaToken}
 * (ou {@code enrollToken} pour l'enrôlement obligatoire de l'administrateur) permet de poursuivre.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        Long userId,
        String email,
        String fullName,
        Role role,
        boolean mustSetPassword,
        boolean emailVerified,
        boolean profileComplete,
        String planTier,
        boolean mfaEnabled,
        boolean mfaRequired,
        boolean mfaEnrollRequired,
        String mfaToken,
        String enrollToken,
        List<String> backupCodes
) {
    public static AuthResponse of(String token, long expiresIn, ma.ensat.hirelink.model.User u, List<String> backupCodes) {
        return new AuthResponse(token, "Bearer", expiresIn, u.getId(), u.getEmail(), u.getFullName(), u.getRole(),
                u.isMustSetPassword(), u.isEmailVerified(), u.isProfileComplete(),
                u.getPlanTier() == null ? null : u.getPlanTier().name(),
                u.isMfaEnabled(), false, false, null, null, backupCodes);
    }

    public static AuthResponse mfaChallenge(String mfaToken) {
        return new AuthResponse(null, null, null, null, null, null, null, false, false, false, null,
                true, true, false, mfaToken, null, null);
    }

    public static AuthResponse mfaEnroll(String enrollToken) {
        return new AuthResponse(null, null, null, null, null, null, null, false, false, false, null,
                false, false, true, null, enrollToken, null);
    }
}
