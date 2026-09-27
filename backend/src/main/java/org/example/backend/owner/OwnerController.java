package org.example.backend.owner;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * /api/owner/me exists to prove the Clerk-JWT security chain end to end —
 * it's not a feature in its own right, but it's also what the frontend calls
 * to confirm "am I logged in as owner" once real feature routes land.
 *
 * /api/owner/me/phone lets her set the number SmsNotifier sends alerts to.
 * There's exactly one owner for the life of this app (see
 * OwnerBootstrapRunner), so this is a one-field update on that same row, not
 * a dedicated settings service — a new service class for one regex check and
 * one save would be abstraction this endpoint doesn't need.
 */
@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final UserRepository userRepository;

    public OwnerController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public OwnerResponse me(@AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER
        // user before this method runs, so the row is guaranteed present.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        return OwnerResponse.from(owner);
    }

    @PostMapping("/me/phone")
    public OwnerResponse setPhoneNumber(@AuthenticationPrincipal Jwt jwt, @RequestBody PhoneNumberRequest request) {
        String phoneNumber = request.phoneNumber() == null ? "" : request.phoneNumber().trim();
        if (!E164.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Enter a phone number in international format, e.g. +233241234567");
        }

        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        owner.setPhoneNumber(phoneNumber);
        userRepository.save(owner);
        return OwnerResponse.from(owner);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record OwnerResponse(Long id, String name, String role, String phoneNumber) {
        static OwnerResponse from(User owner) {
            return new OwnerResponse(owner.getId(), owner.getName(), owner.getRole().name(), owner.getPhoneNumber());
        }
    }

    public record PhoneNumberRequest(String phoneNumber) {
    }

    public record ErrorResponse(String message) {
    }
}
