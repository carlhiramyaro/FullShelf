package org.example.backend.device;

import org.example.backend.security.TokenHasher;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Pairing codes are short and human-typed (the owner reads the code off her
 * screen and types it into the shop browser once), so they're deliberately
 * low-entropy compared to the device/session tokens they mint. The 10-minute
 * expiry and one-time redemption are what keep that safe, not the code's
 * length — matching mvp.md's "one-time code that expires in about 10
 * minutes."
 */
@Service
public class DevicePairingService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no O/0, I/1
    private static final int CODE_LENGTH = 6;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final DeviceRepository deviceRepository;
    private final DevicePairingCodeRepository pairingCodeRepository;
    private final TokenHasher tokenHasher;
    private final SecureRandom random = new SecureRandom();

    public DevicePairingService(DeviceRepository deviceRepository,
                                 DevicePairingCodeRepository pairingCodeRepository, TokenHasher tokenHasher) {
        this.deviceRepository = deviceRepository;
        this.pairingCodeRepository = pairingCodeRepository;
        this.tokenHasher = tokenHasher;
    }

    @Transactional
    public String generateCode(User owner) {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        String rawCode = code.toString();
        pairingCodeRepository.save(
                new DevicePairingCode(tokenHasher.hash(rawCode), owner, Instant.now().plus(CODE_TTL)));
        return rawCode;
    }

    /**
     * Redeems a one-time code into a paired device, returning the raw device
     * token to hand back to the shop browser. Like every token in this app,
     * the raw value is returned once and never stored — only its hash is.
     */
    @Transactional
    public String redeemCode(String rawCode) {
        DevicePairingCode pairingCode = pairingCodeRepository.findByCodeHash(tokenHasher.hash(rawCode))
                .orElseThrow(() -> new InvalidPairingCodeException("Invalid pairing code"));

        if (pairingCode.getRedeemedAt() != null) {
            throw new InvalidPairingCodeException("Pairing code already used");
        }
        if (pairingCode.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidPairingCodeException("Pairing code expired");
        }
        pairingCode.setRedeemedAt(Instant.now());

        String rawDeviceToken = tokenHasher.newRawToken();
        deviceRepository.save(new Device(tokenHasher.hash(rawDeviceToken)));
        return rawDeviceToken;
    }

    @Transactional
    public void revokeDevice(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("No device " + deviceId));
        device.setRevoked(true);
    }
}
