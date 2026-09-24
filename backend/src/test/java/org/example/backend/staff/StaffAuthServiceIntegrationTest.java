package org.example.backend.staff;

import org.example.backend.device.Device;
import org.example.backend.device.DeviceRepository;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres, per this project's convention (see
 * StockLedgerIntegrationTest) — wrapped in @Transactional so nothing written
 * here is ever committed.
 */
@SpringBootTest
@Transactional
class StaffAuthServiceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private StaffManagementService staffManagementService;

    @Autowired
    private StaffAuthService staffAuthService;

    @Autowired
    private StaffSessionRepository staffSessionRepository;

    private Device pairedDevice() {
        return deviceRepository.save(new Device("some-device-token-hash-" + System.nanoTime()));
    }

    @Test
    void correctPinLogsInAndIssuesASession() {
        User staff = staffManagementService.createStaff("Kofi", "1234");
        Device device = pairedDevice();

        String sessionToken = staffAuthService.login(device.getId(), staff.getId(), "1234");

        assertThat(sessionToken).isNotBlank();
        assertThat(staffSessionRepository.findAll()).hasSize(1);
    }

    @Test
    void wrongPinIsRejectedAndCountsTowardLockout() {
        User staff = staffManagementService.createStaff("Kofi", "1234");
        Device device = pairedDevice();

        assertThatThrownBy(() -> staffAuthService.login(device.getId(), staff.getId(), "0000"))
                .isInstanceOf(InvalidPinException.class);

        User reloaded = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(reloaded.getFailedPinAttempts()).isEqualTo(1);
        assertThat(reloaded.isPinLocked()).isFalse();
    }

    @Test
    void fivePinFailuresLocksThePin() {
        User staff = staffManagementService.createStaff("Kofi", "1234");
        Device device = pairedDevice();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> staffAuthService.login(device.getId(), staff.getId(), "0000"))
                    .isInstanceOf(InvalidPinException.class);
        }

        assertThatThrownBy(() -> staffAuthService.login(device.getId(), staff.getId(), "1234"))
                .isInstanceOf(PinLockedException.class);
    }

    @Test
    void resettingThePinClearsTheLockout() {
        User staff = staffManagementService.createStaff("Kofi", "1234");
        Device device = pairedDevice();
        for (int i = 0; i < 5; i++) {
            try {
                staffAuthService.login(device.getId(), staff.getId(), "0000");
            } catch (InvalidPinException ignored) {
                // expected — driving the counter to the lockout threshold
            }
        }

        staffManagementService.resetPin(staff.getId(), "5678");

        String sessionToken = staffAuthService.login(device.getId(), staff.getId(), "5678");
        assertThat(sessionToken).isNotBlank();
    }

    @Test
    void deactivatedStaffCannotLogIn() {
        User staff = staffManagementService.createStaff("Kofi", "1234");
        staffManagementService.deactivate(staff.getId());
        Device device = pairedDevice();

        assertThatThrownBy(() -> staffAuthService.login(device.getId(), staff.getId(), "1234"))
                .isInstanceOf(InvalidPinException.class);
    }

    @Test
    void loggingOutRevokesTheSession() {
        User staff = staffManagementService.createStaff("Kofi", "1234");
        Device device = pairedDevice();
        staffAuthService.login(device.getId(), staff.getId(), "1234");
        Long sessionId = staffSessionRepository.findAll().get(0).getId();

        staffAuthService.logout(sessionId);

        assertThat(staffSessionRepository.findById(sessionId).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void rosterOnlyListsActiveStaff() {
        staffManagementService.createStaff("Kofi", "1234");
        User ama = staffManagementService.createStaff("Ama", "4321");
        staffManagementService.deactivate(ama.getId());

        assertThat(staffAuthService.roster()).extracting(User::getName).containsExactly("Kofi");
    }
}
