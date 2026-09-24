package org.example.backend.staff;

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
class StaffManagementServiceIntegrationTest {

    @Autowired
    private StaffManagementService staffManagementService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createStaffHashesThePinRatherThanStoringItInTheClear() {
        User staff = staffManagementService.createStaff("Kofi", "1234");

        assertThat(staff.getPinHash()).isNotEqualTo("1234");
        assertThat(staff.isActive()).isTrue();
    }

    @Test
    void nonNumericOrWrongLengthPinsAreRejected() {
        assertThatThrownBy(() -> staffManagementService.createStaff("Kofi", "12a4"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> staffManagementService.createStaff("Kofi", "12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivatingStaffFlipsActiveFalse() {
        User staff = staffManagementService.createStaff("Kofi", "1234");

        staffManagementService.deactivate(staff.getId());

        assertThat(userRepository.findById(staff.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void listStaffOnlyReturnsStaffNotOwners() {
        staffManagementService.createStaff("Kofi", "1234");

        assertThat(staffManagementService.listStaff()).extracting(User::getName).containsExactly("Kofi");
    }
}
