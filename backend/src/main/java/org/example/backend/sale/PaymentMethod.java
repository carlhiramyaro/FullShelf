package org.example.backend.sale;

/** How a sale was paid. Recorded only: nothing here processes a payment. */
public enum PaymentMethod {
    CASH,
    MOMO;

    /** Missing means CASH (an old frontend during a deploy); anything else unknown is rejected. */
    public static PaymentMethod parse(String value) {
        if (value == null) {
            return CASH;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Payment method must be CASH or MOMO");
        }
    }
}
