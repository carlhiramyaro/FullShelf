package org.example.backend.expense;

/** Fixed list for v1 (finance-scope.md); a note carries the detail. */
public enum ExpenseCategory {
    RENT("Rent"),
    SALARIES("Salaries"),
    TRANSPORT("Transport"),
    ELECTRICITY_WATER("Electricity & water"),
    PACKAGING("Packaging"),
    SUPPLIES("Supplies"),
    OTHER("Other");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ExpenseCategory parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pick a category");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category: " + value);
        }
    }
}
