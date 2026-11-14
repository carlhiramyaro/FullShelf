package org.example.backend.expense;

/**
 * Where the money came from. CASH reduces expected cash, MOMO reduces expected MoMo,
 * OTHER (bank, the owner's pocket, supplier credit) reduces neither.
 */
public enum ExpensePaidFrom {
    CASH,
    MOMO,
    OTHER;

    public static ExpensePaidFrom parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Say where it was paid from");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Paid from must be CASH, MOMO or OTHER");
        }
    }
}
