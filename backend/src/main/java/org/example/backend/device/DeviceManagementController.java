package org.example.backend.device;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner-only device management: generating a pairing code and revoking a
 * paired device. Sits under /api/owner/** so it's already gated by the
 * existing Clerk-JWT chain from Phase A — no new security code needed here.
 */
@RestController
@RequestMapping("/api/owner/devices")
public class DeviceManagementController {

    private final DevicePairingService devicePairingService;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public DeviceManagementController(DevicePairingService devicePairingService, DeviceRepository deviceRepository,
                                       UserRepository userRepository) {
        this.devicePairingService = devicePairingService;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/pairing-code")
    public PairingCodeResponse generatePairingCode(@AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER user.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        return new PairingCodeResponse(devicePairingService.generateCode(owner));
    }

    @GetMapping
    public List<DeviceResponse> list() {
        return deviceRepository.findAll().stream().map(DeviceResponse::from).toList();
    }

    @PostMapping("/{id}/revoke")
    public void revoke(@PathVariable Long id) {
        devicePairingService.revokeDevice(id);
    }

    public record PairingCodeResponse(String code) {
    }

    public record DeviceResponse(Long id, boolean revoked) {
        static DeviceResponse from(Device device) {
            return new DeviceResponse(device.getId(), device.isRevoked());
        }
    }
}
