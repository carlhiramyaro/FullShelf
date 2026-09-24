package org.example.backend.staff;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Owns the owner-side half of staff PIN sessions: create, reset, deactivate.
 * PINs are 4-digit numeric (mvp.md's tap+PIN flow) and BCrypt-hashed — a
 * slow, salted hash is worth its cost precisely because a 4-digit PIN is
 * low-entropy and would otherwise be fast to brute-force offline, unlike the
 * random high-entropy tokens TokenHasher handles elsewhere in this app.
 */
@Service
public class StaffManagementService {

    private static final Pattern PIN_PATTERN = Pattern.compile("\\d{4}");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createStaff(String name, String pin) {
        validatePin(pin);
        User staff = new User(name, UserRole.STAFF);
        staff.setPinHash(passwordEncoder.encode(pin));
        return userRepository.save(staff);
    }

    @Transactional
    public void resetPin(Long staffId, String newPin) {
        validatePin(newPin);
        User staff = getStaff(staffId);
        staff.setPinHash(passwordEncoder.encode(newPin));
        staff.setFailedPinAttempts(0);
        staff.setPinLocked(false);
    }

    @Transactional
    public void deactivate(Long staffId) {
        getStaff(staffId).setActive(false);
    }

    public List<User> listStaff() {
        return userRepository.findByRoleOrderByName(UserRole.STAFF);
    }

    private User getStaff(Long staffId) {
        User user = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("No staff " + staffId));
        if (user.getRole() != UserRole.STAFF) {
            throw new IllegalArgumentException("User " + staffId + " is not staff");
        }
        return user;
    }

    private void validatePin(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin).matches()) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits");
        }
    }
}
