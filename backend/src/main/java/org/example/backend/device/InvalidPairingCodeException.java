package org.example.backend.device;

public class InvalidPairingCodeException extends RuntimeException {

    public InvalidPairingCodeException(String message) {
        super(message);
    }
}
