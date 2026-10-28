package ma.ensat.hirelink.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.SessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SessionService sessionService;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository, SessionService sessionService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                var claims = jwtService.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                String sid = claims.get("sid", String.class);
                // État du compte et de la session vérifié à CHAQUE requête : suspension, désactivation et révocation sont immédiates.
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.isActive() && sessionService.isActive(sid, userId)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                    );
                    auth.setDetails(sid);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
