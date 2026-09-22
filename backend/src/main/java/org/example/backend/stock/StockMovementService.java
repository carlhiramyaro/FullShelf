package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.sale.SaleLine;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Owns every write to the stock ledger. Opening stock, receive stock, sale
 * and write-off all call {@link #record}; void and reverse-entry both call
 * {@link #reverse} — the one primitive the build plan calls out as shared
 * between them.
 */
@Service
public class StockMovementService {

    private static final Set<StockMovementType> REVERSAL_TYPES = EnumSet.of(
            StockMovementType.VOID, StockMovementType.REVERSAL);

    private final StockMovementRepository stockMovementRepository;

    public StockMovementService(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public StockMovement record(Product product, BigDecimal signedQuantity, StockMovementType type,
                                 User performedBy, SaleLine saleLine, String note) {
        StockMovement movement = new StockMovement(product, signedQuantity, type, performedBy);
        movement.setSaleLine(saleLine);
        movement.setNote(note);
        return stockMovementRepository.save(movement);
    }

    /**
     * Negates {@code original} and links the new row back to it. The original
     * row is left untouched — reversal is always an addition to the ledger,
     * never an edit — so the mistake stays visible for audit.
     */
    @Transactional
    public StockMovement reverse(StockMovement original, StockMovementType reversalType, User performedBy,
                                  String note) {
        if (!REVERSAL_TYPES.contains(reversalType)) {
            throw new IllegalArgumentException("reversalType must be VOID or REVERSAL, got " + reversalType);
        }
        if (stockMovementRepository.existsByReversedMovementId(original.getId())) {
            throw new IllegalStateException("Movement " + original.getId() + " has already been reversed");
        }

        StockMovement reversal = new StockMovement(original.getProduct(), original.getQuantity().negate(),
                reversalType, performedBy);
        reversal.setReversedMovement(original);
        reversal.setSaleLine(original.getSaleLine());
        reversal.setNote(note);
        return stockMovementRepository.save(reversal);
    }
}
