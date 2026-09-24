package org.example.backend.staff;

public class PinLockedException extends RuntimeException {

    public PinLockedException(String message) {
        super(message);
    }
}
