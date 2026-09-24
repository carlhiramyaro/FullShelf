package org.example.backend.staff;

import org.example.backend.device.Device;
import org.example.backend.device.DeviceRepository;
import org.example.backend.security.TokenHasher;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Staff PIN sessions are a separate scheme from Clerk (owner-only, per
 * CLAUDE.md): login is only reachable once StaffAccessFilter has already
 * resolved a valid device token, so {@code deviceId} here is trusted rather
 * than re-validated. "Wrong staff id" and "wrong PIN" deliberately raise the
 * same InvalidPinException with the same message, so a login attempt can't
 * be used to probe which staff ids exist.
 */
@Service
public class StaffAuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final StaffSessionRepository staffSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;

    public StaffAuthService(UserRepository userRepository, DeviceRepository deviceRepository,
                             StaffSessionRepository staffSessionRepository, PasswordEncoder passwordEncoder,
                             TokenHasher tokenHasher) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.staffSessionRepository = staffSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
    }

    public List<User> roster() {
        return userRepository.findByRoleAndActiveTrueOrderByName(UserRole.STAFF);
    }

    @Transactional
    public String login(Long deviceId, Long staffId, String pin) {
        Device device = deviceRepository.getReferenceById(deviceId);

        User staff = userRepository.findById(staffId)
                .filter(u -> u.getRole() == UserRole.STAFF && u.isActive())
                .orElseThrow(() -> new InvalidPinException("Incorrect PIN"));

        if (staff.isPinLocked()) {
            throw new PinLockedException("PIN is locked — ask Aunt Amerley to reset it");
        }
        if (!passwordEncoder.matches(pin, staff.getPinHash())) {
            registerFailedAttempt(staff);
            throw new InvalidPinException("Incorrect PIN");
        }

        staff.setFailedPinAttempts(0);

        String rawSessionToken = tokenHasher.newRawToken();
        staffSessionRepository.save(new StaffSession(staff, device, tokenHasher.hash(rawSessionToken)));
        return rawSessionToken;
    }

    @Transactional
    public void logout(Long sessionId) {
        staffSessionRepository.findById(sessionId).ifPresent(session -> session.setRevoked(true));
    }

    private void registerFailedAttempt(User staff) {
        staff.setFailedPinAttempts(staff.getFailedPinAttempts() + 1);
        if (staff.getFailedPinAttempts() >= MAX_FAILED_ATTEMPTS) {
            staff.setPinLocked(true);
        }
    }
}
