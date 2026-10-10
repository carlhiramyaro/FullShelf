package org.example.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.shop.CurrentShop;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs on /api/owner/** after the Clerk JWT is verified: maps the token's
 * subject to the active owner row and sets that owner's shop as the current
 * shop. If there is no matching active owner the shop stays unset; the
 * chain's ROLE_OWNER check rejects the request anyway.
 *
 * Not a @Component, so Spring Boot doesn't also register it globally; it is
 * created in SecurityConfig and wired into the owner chain only.
 */
class OwnerShopFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final CurrentShop currentShop;

    OwnerShopFilter(UserRepository userRepository, CurrentShop currentShop) {
        this.userRepository = userRepository;
        this.currentShop = currentShop;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                userRepository.findByClerkUserId(jwt.getSubject())
                        .filter(user -> user.isActive() && user.getRole() == UserRole.OWNER)
                        .ifPresent(owner -> currentShop.set(owner.getShop()));
            }
            chain.doFilter(request, response);
        } finally {
            currentShop.clear();
        }
    }
}
