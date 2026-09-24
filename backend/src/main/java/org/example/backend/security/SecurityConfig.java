package org.example.backend.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Three independently-scoped filter chains: /api/owner/** is Clerk-JWT-only,
 * /api/staff/** is gated by StaffAccessFilter's device/session tokens (a
 * separate scheme from Clerk, per CLAUDE.md — staff use custom PIN
 * sessions), and everything else (including the public /api/devices/pair)
 * is left open for now. Kept scoped by path prefix rather than one global
 * chain so the two real auth schemes never have to share a configuration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;
    private final StaffAccessFilter staffAccessFilter;

    public SecurityConfig(OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter,
                           StaffAccessFilter staffAccessFilter) {
        this.ownerJwtAuthoritiesConverter = ownerJwtAuthoritiesConverter;
        this.staffAccessFilter = staffAccessFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * StaffAccessFilter is a @Component so it can be injected here via
     * addFilterBefore, but that also makes Spring Boot auto-register it as a
     * global servlet filter running in front of every chain (including
     * /api/owner/** and the public /api/devices/pair) — this disables that
     * automatic registration so it only runs where staffFilterChain wires it.
     */
    @Bean
    public FilterRegistrationBean<StaffAccessFilter> disableStaffAccessFilterAutoRegistration(
            StaffAccessFilter staffAccessFilter) {
        FilterRegistrationBean<StaffAccessFilter> registration = new FilterRegistrationBean<>(staffAccessFilter);
        registration.setEnabled(false);
        return registration;
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

    @Bean
    @Order(2)
    public SecurityFilterChain staffFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/staff/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .addFilterBefore(staffAccessFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Covers everything else, including the public /api/devices/pair (the
    // one-time code is the credential there, not a security-chain check).
    // Left open rather than defaulting to deny-all, which would also block
    // Spring Boot's own error-handling paths for routes not yet built.
    @Bean
    @Order(3)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());

        return http.build();
    }
}
