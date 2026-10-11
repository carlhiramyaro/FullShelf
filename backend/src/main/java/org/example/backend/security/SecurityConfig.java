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
import org.example.backend.shop.CurrentShop;
import org.example.backend.user.UserRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Four independently-scoped filter chains: /api/owner/** is Clerk-JWT-only,
 * /api/admin/** is Clerk-JWT for the one platform-admin id,
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
    private final AdminJwtAuthoritiesConverter adminJwtAuthoritiesConverter;
    private final StaffAccessFilter staffAccessFilter;

    public SecurityConfig(OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter,
                           AdminJwtAuthoritiesConverter adminJwtAuthoritiesConverter,
                           StaffAccessFilter staffAccessFilter) {
        this.ownerJwtAuthoritiesConverter = ownerJwtAuthoritiesConverter;
        this.adminJwtAuthoritiesConverter = adminJwtAuthoritiesConverter;
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
    public SecurityFilterChain ownerFilterChain(HttpSecurity http, UserRepository userRepository,
                                                 CurrentShop currentShop) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(ownerJwtAuthoritiesConverter);

        http.securityMatcher("/api/owner/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("OWNER"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(new OwnerShopFilter(userRepository, currentShop),
                        BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The platform admin (Carl): same Clerk JWKS as owners, but authorised by
     * one Clerk user id from configuration, with no shop attached. No
     * OwnerShopFilter, so CurrentShop is never set on these routes.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(adminJwtAuthoritiesConverter);

        http.securityMatcher("/api/admin/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("ADMIN"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    @Order(3)
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
    @Order(4)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());

        return http.build();
    }
}
