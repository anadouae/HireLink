package ma.ensat.hirelink.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Adresse IP et navigateur de l'appelant (utilisés pour le journal d'audit, les sessions et le rate limiting). */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 300) ua = ua.substring(0, 300);
        return new ClientInfo(req.getRemoteAddr(), ua == null ? "" : ua.replaceAll("[\\r\\n]", " "));
    }

    /** Contexte de la requête en cours (vide en dehors d'une requête HTTP). */
    public static ClientInfo current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            return from(a.getRequest());
        }
        return new ClientInfo("-", "");
    }
}
