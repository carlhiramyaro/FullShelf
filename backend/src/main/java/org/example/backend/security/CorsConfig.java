package org.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Only /api/staff/** and /api/devices/** need CORS: the shop kiosk is a
 * plain browser page calling this API directly with fetch(). Owner
 * mutations go through Next.js Server Actions instead (server calling
 * server), which never triggers a browser CORS check, so /api/owner/** is
 * deliberately left out of this configuration.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-origin:http://localhost:3000}") String frontendOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-Device-Token", "X-Staff-Session-Token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/staff/**", configuration);
        source.registerCorsConfiguration("/api/devices/**", configuration);
        return source;
    }
}
