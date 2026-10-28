package ma.ensat.hirelink.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Plafond global par adresse IP (filet de sécurité contre le scraping et le déni de service applicatif simple). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RateLimitFilter extends OncePerRequestFilter {

    private final AuthThrottle throttle;
    private final int perMinute;

    public RateLimitFilter(AuthThrottle throttle, @Value("${hirelink.ratelimit.per-ip-per-minute:600}") int perMinute) {
        this.throttle = throttle;
        this.perMinute = perMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getMethod().equals("OPTIONS") || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            throttle.hit("global:" + request.getRemoteAddr(), perMinute, 60, "Trop de requêtes. Réessayez dans un instant.");
        } catch (TooManyRequestsException e) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":429,\"message\":\"" + e.getMessage() + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
