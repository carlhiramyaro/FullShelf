package org.example.backend.device;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
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
class DevicePairingServiceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DevicePairingService devicePairingService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Test
    void redeemingAFreshCodePairsANewUnrevokedDevice() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        String code = devicePairingService.generateCode(owner);

        String deviceToken = devicePairingService.redeemCode(code);

        assertThat(deviceToken).isNotBlank();
        assertThat(deviceRepository.findAll()).hasSize(1);
        assertThat(deviceRepository.findAll().get(0).isRevoked()).isFalse();
    }

    @Test
    void redeemingTheSameCodeTwiceIsRejected() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        String code = devicePairingService.generateCode(owner);
        devicePairingService.redeemCode(code);

        assertThatThrownBy(() -> devicePairingService.redeemCode(code))
                .isInstanceOf(InvalidPairingCodeException.class);
    }

    @Test
    void redeemingAnUnknownCodeIsRejected() {
        assertThatThrownBy(() -> devicePairingService.redeemCode("BADCODE"))
                .isInstanceOf(InvalidPairingCodeException.class);
    }

    @Test
    void revokingADeviceStopsItBeingPaired() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        String code = devicePairingService.generateCode(owner);
        devicePairingService.redeemCode(code);
        Long deviceId = deviceRepository.findAll().get(0).getId();

        devicePairingService.revokeDevice(deviceId);

        assertThat(deviceRepository.findById(deviceId).orElseThrow().isRevoked()).isTrue();
    }
}
