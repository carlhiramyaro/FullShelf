package org.example.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two independently-scoped filter chains rather than one: /api/owner/** is
 * Clerk-JWT-only, and everything else is left open for now. Phase B's staff
 * PIN-session auth will add its own chain for /api/staff/** alongside this
 * one, rather than the two schemes having to share a single configuration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;

    public SecurityConfig(OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter) {
        this.ownerJwtAuthoritiesConverter = ownerJwtAuthoritiesConverter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain ownerFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(ownerJwtAuthoritiesConverter);

        http.securityMatcher("/api/owner/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("OWNER"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    // Nothing else exists yet (Phase B adds the staff chain). Left open rather
    // than defaulting to deny-all, which would also block Spring Boot's own
    // error-handling paths for routes not yet built.
    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
