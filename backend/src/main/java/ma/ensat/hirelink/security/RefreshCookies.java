package ma.ensat.hirelink.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cookie du jeton de rafraîchissement : HttpOnly (illisible par JavaScript, donc par un XSS), SameSite=Strict,
 * limité au chemin /api/v1/auth, Secure en HTTPS (obligatoire en production).
 * Protection CSRF des endpoints qui l'utilisent : en-tête personnalisé obligatoire + contrôle de l'en-tête Origin.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "hl_rt";
    public static final String CSRF_HEADER = "X-HireLink-CSRF";
    private static final String PATH = "/api/v1/auth";

    private final boolean secureAlways;
    private final Set<String> allowedOrigins;

    public RefreshCookies(
            @Value("${hirelink.cookie.secure:false}") boolean secureAlways,
            @Value("${hirelink.cors.allowed-origins}") String origins
    ) {
        this.secureAlways = secureAlways;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public void write(HttpServletResponse res, HttpServletRequest req, String token, long maxAgeSeconds) {
        ResponseCookie c = ResponseCookie.from(NAME, token)
                .httpOnly(true).secure(secureAlways || req.isSecure()).sameSite("Strict")
                .path(PATH).maxAge(Duration.ofSeconds(Math.max(1, maxAgeSeconds))).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    public void clear(HttpServletResponse res, HttpServletRequest req) {
        ResponseCookie c = ResponseCookie.from(NAME, "")
                .httpOnly(true).secure(secureAlways || req.isSecure()).sameSite("Strict")
                .path(PATH).maxAge(Duration.ZERO).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    public String read(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if (NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    /** À appeler avant toute action authentifiée uniquement par ce cookie. */
    public void assertCsrf(HttpServletRequest req) {
        if (req.getHeader(CSRF_HEADER) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requête refusée (protection CSRF).");
        }
        String origin = req.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origine non autorisée.");
        }
    }
}
