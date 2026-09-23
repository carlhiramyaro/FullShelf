package org.example.backend.security;

import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Resolves a Clerk JWT's `sub` claim to our own users row and grants
 * ROLE_OWNER only if that row exists, is active, and has role OWNER. The JWT
 * alone only proves who signed in with Clerk — this is the actual
 * authorization check.
 */
@Component
public class OwnerJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserRepository userRepository;

    public OwnerJwtAuthoritiesConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return userRepository.findByClerkUserId(jwt.getSubject())
                .filter(user -> user.isActive() && user.getRole() == UserRole.OWNER)
                .<Collection<GrantedAuthority>>map(user -> List.of(new SimpleGrantedAuthority("ROLE_OWNER")))
                .orElseGet(List::of);
    }
}
