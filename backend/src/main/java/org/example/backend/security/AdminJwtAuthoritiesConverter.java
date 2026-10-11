package org.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Grants ROLE_ADMIN only to the one Clerk user id in configuration. A blank
 * setting grants it to nobody, so a missing env var fails closed.
 */
@Component
public class AdminJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String adminClerkUserId;

    public AdminJwtAuthoritiesConverter(@Value("${app.admin-clerk-user-id:}") String adminClerkUserId) {
        this.adminClerkUserId = adminClerkUserId == null ? "" : adminClerkUserId.trim();
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!adminClerkUserId.isEmpty() && adminClerkUserId.equals(jwt.getSubject())) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of();
    }
}
