package org.example.backend.stock;

import org.example.backend.shop.Shop;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Every balance in the shop in one grouped query, for the list screens. A
     * product with no movements has no key; callers use getOrDefault(id, ZERO),
     * the same zero currentBalance returns.
     */
    public Map<Long, BigDecimal> currentBalances(Shop shop) {
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (Object[] row : stockMovementRepository.sumQuantityByProductForShop(shop)) {
            balances.put((Long) row[0], (BigDecimal) row[1]);
        }
        return balances;
    }

    /** Unreversed movements of the given types per product, in one grouped query. Absent key means zero. */
    public Map<Long, Long> unreversedCounts(Shop shop, Collection<StockMovementType> types) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : stockMovementRepository.countUnreversedByProductForShop(shop, types)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
