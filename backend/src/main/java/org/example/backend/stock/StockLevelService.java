package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.shop.CurrentShop;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of every active product's current balance against its
 * alert level, derived from the ledger via StockBalanceService — mvp.md's
 * "Stock per system" screen. The NEGATIVE/LOW/OK bucketing lives here
 * rather than in the controller because it's a real business rule (the
 * orange-vs-red distinction CLAUDE.md's palette encodes, and the same
 * low-stock/negative-stock split Dashboard will need next) worth testing on
 * its own.
 */
@Service
public class StockLevelService {

    private final ProductRepository productRepository;
    private final StockBalanceService stockBalanceService;
    private final CurrentShop currentShop;

    public StockLevelService(ProductRepository productRepository, StockBalanceService stockBalanceService,
                                CurrentShop currentShop) {
        this.productRepository = productRepository;
        this.stockBalanceService = stockBalanceService;
        this.currentShop = currentShop;
    }

    public List<ProductStockLevel> listLevels() {
        Map<Long, BigDecimal> balances = stockBalanceService.currentBalances(currentShop.get());
        return productRepository.findByShopOrderByName(currentShop.get()).stream()
                .filter(Product::isActive)
                .map(product -> {
                    BigDecimal balance = balances.getOrDefault(product.getId(), BigDecimal.ZERO);
                    return new ProductStockLevel(product, balance,
                            StockLevelStatus.forBalance(balance, product.getAlertLevel()));
                })
                .toList();
    }

    /**
     * Value of stock on hand: sum of max(balance, 0) x cost over active products. A negative
     * balance counts as zero, not negative value. Products with no cost can't be valued, so
     * they are left out and counted, and the screen says so instead of understating quietly.
     */
    public StockValue stockValue() {
        return stockValue(listLevels());
    }

    /** For callers that already hold the levels (the dashboard), so the balances aren't read twice. */
    public StockValue stockValue(List<ProductStockLevel> levels) {
        BigDecimal total = BigDecimal.ZERO;
        int withoutCost = 0;
        for (ProductStockLevel level : levels) {
            BigDecimal cost = level.product().getCostPrice();
            if (cost == null) {
                withoutCost++;
            } else if (level.currentBalance().signum() > 0) {
                total = total.add(level.currentBalance().multiply(cost));
            }
        }
        return new StockValue(total.setScale(2, RoundingMode.HALF_UP), withoutCost);
    }

    public record StockValue(BigDecimal value, int productsWithoutCost) {
    }

    public record ProductStockLevel(Product product, BigDecimal currentBalance, StockLevelStatus status) {
    }
}
