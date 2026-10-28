package ma.ensat.hirelink.dto;

/** Corps JSON + (si une session vient d'être ouverte ou renouvelée) jeton de rafraîchissement à poser en cookie HttpOnly. */
public record AuthResult(AuthResponse body, String refreshToken, long refreshMaxAgeSeconds) {
    public static AuthResult withoutSession(AuthResponse body) {
        return new AuthResult(body, null, 0);
    }
}
