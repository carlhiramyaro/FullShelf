package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Owner-only reversal of a mistyped opening or received line. mvp.md scopes
 * this to those two types only — write-off and sale corrections are separate
 * features (write-off has its own screen, sale correction is Phase E's Void,
 * which uses the same StockMovementService.reverse primitive but tags VOID
 * instead of REVERSAL). Re-entering the correct figure isn't part of this
 * slice: it's a second call into OpeningStockService/ReceiveStockService,
 * same as any other opening/received entry — see docs/decisions.md.
 */
@Service
public class ReverseStockService {

    private static final Set<StockMovementType> REVERSIBLE_TYPES =
            EnumSet.of(StockMovementType.OPENING, StockMovementType.RECEIVED);

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockMovementService stockMovementService;
    private final StockBalanceService stockBalanceService;

    public ReverseStockService(ProductRepository productRepository,
                                StockMovementRepository stockMovementRepository,
                                StockMovementService stockMovementService,
                                StockBalanceService stockBalanceService) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockMovementService = stockMovementService;
        this.stockBalanceService = stockBalanceService;
    }

    public List<ProductReverseStatus> listStatus() {
        return productRepository.findAllByOrderByName().stream()
                .filter(Product::isActive)
                .map(product -> new ProductReverseStatus(product, reversibleMovements(product.getId()).size(),
                        stockBalanceService.currentBalance(product.getId())))
                .toList();
    }

    public List<StockMovement> reversibleMovements(Long productId) {
        return stockMovementRepository.findByProductIdAndTypeInOrderByCreatedAtDesc(productId, REVERSIBLE_TYPES)
                .stream()
                .filter(movement -> !stockMovementRepository.existsByReversedMovementId(movement.getId()))
                .toList();
    }

    @Transactional
    public StockMovement reverseEntry(Long movementId, String note, User owner) {
        StockMovement original = getMovement(movementId);
        if (!REVERSIBLE_TYPES.contains(original.getType())) {
            throw new IllegalArgumentException("Only an opening or received line can be reversed");
        }
        // Already-reversed is checked by StockMovementService.reverse itself
        // (IllegalStateException) rather than duplicated here.
        return stockMovementService.reverse(original, StockMovementType.REVERSAL, owner, blankToNull(note));
    }

    private StockMovement getMovement(Long id) {
        return stockMovementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No stock movement " + id));
    }

    private String blankToNull(String note) {
        return (note == null || note.isBlank()) ? null : note.trim();
    }

    public record ProductReverseStatus(Product product, int reversibleCount, BigDecimal currentBalance) {
    }
}
