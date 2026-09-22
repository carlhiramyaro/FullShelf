package org.example.backend.stock;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Derives current stock from the ledger rather than a cached column, so there
 * is exactly one source of truth and no drift to reconcile — see mvp.md:
 * "Stock per system (received − sold − written off) is the truth."
 */
@Service
public class StockBalanceService {

    private final StockMovementRepository stockMovementRepository;

    public StockBalanceService(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }

    public BigDecimal currentBalance(Long productId) {
        return stockMovementRepository.sumQuantityByProductId(productId).orElse(BigDecimal.ZERO);
    }
}
