package org.example.backend.staff;

import org.example.backend.device.DevicePrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gated by StaffAccessFilter, not Spring's normal authorizeHttpRequests
 * role checks: /roster and /login need a device token, everything else
 * needs a staff session token. See SecurityConfig's staffFilterChain.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffAuthService staffAuthService;

    public StaffController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @GetMapping("/roster")
    public List<RosterEntry> roster() {
        return staffAuthService.roster().stream()
                .map(user -> new RosterEntry(user.getId(), user.getName()))
                .toList();
    }

    @PostMapping("/login")
    public LoginResponse login(@AuthenticationPrincipal DevicePrincipal device, @RequestBody LoginRequest request) {
        String sessionToken = staffAuthService.login(device.deviceId(), request.staffId(), request.pin());
        return new LoginResponse(sessionToken);
    }

    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal StaffPrincipal staff) {
        staffAuthService.logout(staff.sessionId());
    }

    @ExceptionHandler({InvalidPinException.class, PinLockedException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthFailure(RuntimeException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record RosterEntry(Long id, String name) {
    }

    public record LoginRequest(Long staffId, String pin) {
    }

    public record LoginResponse(String sessionToken) {
    }

    public record ErrorResponse(String message) {
    }
}
