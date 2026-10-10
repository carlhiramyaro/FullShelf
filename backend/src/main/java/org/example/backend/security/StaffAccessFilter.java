package org.example.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.device.Device;
import org.example.backend.device.DevicePrincipal;
import org.example.backend.device.DeviceRepository;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.staff.StaffPrincipal;
import org.example.backend.staff.StaffSession;
import org.example.backend.staff.StaffSessionRepository;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Gatekeeps /api/staff/**. Roster and login only need a paired shop device
 * (X-Device-Token); every other staff route needs an active PIN session
 * (X-Staff-Session-Token). One filter switching on path, rather than two
 * security chains, since both token kinds share the same header-based
 * lookup shape and the set of device-only routes is small and fixed.
 */
@Component
public class StaffAccessFilter extends OncePerRequestFilter {

    private static final Set<String> DEVICE_ONLY_PATHS = Set.of("/api/staff/roster", "/api/staff/login");

    private final DeviceRepository deviceRepository;
    private final StaffSessionRepository staffSessionRepository;
    private final TokenHasher tokenHasher;
    private final CurrentShop currentShop;

    public StaffAccessFilter(DeviceRepository deviceRepository, StaffSessionRepository staffSessionRepository,
                              TokenHasher tokenHasher, CurrentShop currentShop) {
        this.deviceRepository = deviceRepository;
        this.staffSessionRepository = staffSessionRepository;
        this.tokenHasher = tokenHasher;
        this.currentShop = currentShop;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (DEVICE_ONLY_PATHS.contains(request.getRequestURI())) {
            if (!authenticateDevice(request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unpaired or revoked device");
                return;
            }
        } else if (!authenticateStaffSession(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired — please log in again");
            return;
        }

        try {
            chain.doFilter(request, response);
        } finally {
            currentShop.clear();
        }
    }

    private boolean authenticateDevice(HttpServletRequest request) {
        String rawToken = request.getHeader("X-Device-Token");
        if (rawToken == null) {
            return false;
        }
        Device device = deviceRepository.findByTokenHashAndRevokedFalse(tokenHasher.hash(rawToken)).orElse(null);
        if (device == null) {
            return false;
        }
        currentShop.set(device.getShop());
        setAuthentication(new DevicePrincipal(device.getId()), "ROLE_DEVICE");
        return true;
    }

    private boolean authenticateStaffSession(HttpServletRequest request) {
        String rawToken = request.getHeader("X-Staff-Session-Token");
        if (rawToken == null) {
            return false;
        }
        StaffSession session = staffSessionRepository.findActiveWithShops(tokenHasher.hash(rawToken))
                .orElse(null);
        if (session == null) {
            return false;
        }
        // The session must sit on a still-paired device, and the staff member
        // must belong to that device's shop.
        Shop shop = session.getUser().getShop();
        if (session.getDevice().isRevoked() || !shop.getId().equals(session.getDevice().getShop().getId())) {
            return false;
        }
        currentShop.set(shop);
        setAuthentication(new StaffPrincipal(session.getUser().getId(), session.getId(), shop.getId()), "ROLE_STAFF");
        return true;
    }

    private void setAuthentication(Object principal, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TokenAuthentication(principal, authority));
    }

    private static final class TokenAuthentication extends AbstractAuthenticationToken {

        private final Object principal;

        TokenAuthentication(Object principal, String authority) {
            super(List.of(new SimpleGrantedAuthority(authority)));
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }
}
