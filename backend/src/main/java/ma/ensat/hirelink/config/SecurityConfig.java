package ma.ensat.hirelink.config;

import jakarta.servlet.http.HttpServletResponse;
import ma.ensat.hirelink.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${hirelink.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${hirelink.swagger.enabled:true}")
    private boolean swaggerEnabled;

    @Value("${hirelink.security.bcrypt-strength:12}")
    private int bcryptStrength;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API sans cookie de session : l'authentification est un jeton Bearer (CSRF non applicable).
                // Le seul cookie (jeton de rafraîchissement) est SameSite=Strict + en-tête CSRF + contrôle d'Origin.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, e) -> json(res, 401, "Authentification requise ou session expirée."))
                        .accessDeniedHandler((req, res, e) -> json(res, 403, "Accès refusé.")))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/v1/health",
                            "/api/v1/meta",
                            "/api/v1/auth/config",
                            "/api/v1/auth/request-otp",
                            "/api/v1/auth/verify-register-otp",
                            "/api/v1/auth/verify-login-otp",
                            "/api/v1/auth/login",
                            "/api/v1/auth/google",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout",
                            "/api/v1/auth/forgot-password",
                            "/api/v1/auth/reset-password",
                            "/api/v1/auth/mfa/verify",
                            "/api/v1/auth/mfa/enroll/start",
                            "/api/v1/auth/mfa/enroll/finish",
                            "/api/v1/auth/passkey/login/options",
                            "/api/v1/auth/passkey/login/finish"
                    ).permitAll();
                    if (swaggerEnabled) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll();
                    } else {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").denyAll();
                    }
                    auth.requestMatchers("/error").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/offers/public").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/organizations/public").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/notifications/stream").permitAll() // authentifié par ticket à usage unique
                            .requestMatchers(HttpMethod.GET, "/api/v1/media/org-logos/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/catalog/suggest", "/api/v1/catalog/places").permitAll()
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMINISTRATEUR")
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void json(HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    /** BCrypt coût 12 (≈ 250 ms) ; les anciens hachés (coût 10) restent valides et sont relevés à la connexion. */
    /** Aucun utilisateur « par défaut » : empêche Spring Boot de générer (et d'afficher dans les logs) un mot de passe temporaire. */
    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("n/a"); };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(Math.max(12, bcryptStrength));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(o -> !o.isEmpty() && !o.equals("*") && !o.equals("null")).toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-HireLink-CSRF"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true); // nécessaire pour le cookie HttpOnly de rafraîchissement ; origines explicites uniquement
        config.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
