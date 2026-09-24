package org.example.backend.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared by every opaque bearer credential in this app (device tokens, staff
 * session tokens, device pairing codes): a high-entropy raw value is handed
 * to the client once, and only its SHA-256 hash is ever stored, so a
 * database leak doesn't hand out live credentials. This is a fast, unsalted
 * hash rather than BCrypt deliberately — these tokens are random and
 * high-entropy (unlike a 4-digit PIN), so a slow hash would only add lookup
 * cost with no security benefit.
 */
@Component
public class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
