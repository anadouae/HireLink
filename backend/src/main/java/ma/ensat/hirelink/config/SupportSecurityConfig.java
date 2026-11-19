package ma.ensat.hirelink.config;

import ma.ensat.hirelink.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Formulaire Support ouvert aux visiteurs : seul POST /api/v1/support passe par cette chaîne.
 * Le jeton, s'il est présent, est lu pour rattacher la demande au compte ; sinon la demande reste anonyme.
 * La lecture des demandes (/api/v1/admin/support) reste dans la chaîne principale (rôle administrateur).
 */
@Configuration
public class SupportSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain supportFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .securityMatcher(new AntPathRequestMatcher("/api/v1/support", HttpMethod.POST.name()))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
