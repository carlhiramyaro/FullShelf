package org.example.backend.owner;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/owner/me exists to prove the Clerk-JWT security chain end to end —
 * it's not a feature in its own right, but it's also what the frontend calls
 * to confirm "am I logged in as owner" once real feature routes land.
 */
@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private final UserRepository userRepository;

    public OwnerController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public OwnerResponse me(@AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER
        // user before this method runs, so the row is guaranteed present.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        return new OwnerResponse(owner.getId(), owner.getName(), owner.getRole().name());
    }

    public record OwnerResponse(Long id, String name, String role) {
    }
}
