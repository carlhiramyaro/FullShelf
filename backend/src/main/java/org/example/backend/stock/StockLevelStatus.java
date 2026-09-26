package org.example.backend.stock;

import java.math.BigDecimal;

public enum StockLevelStatus {
    NEGATIVE,
    LOW,
    OK;

    /**
     * NEGATIVE takes priority over LOW when both would technically apply
     * (balance below zero and at/below the alert level) — mvp.md calls out
     * negative stock as its own, more serious, flagged condition. Shared by
     * StockLevelService (the Stock per system screen) and
     * StockCrossingListener (alert crossing detection) so the two don't risk
     * drifting into two different definitions of "low."
     */
    public static StockLevelStatus forBalance(BigDecimal balance, BigDecimal alertLevel) {
        if (balance.signum() < 0) {
            return NEGATIVE;
        }
        if (balance.compareTo(alertLevel) <= 0) {
            return LOW;
        }
        return OK;
    }
}
