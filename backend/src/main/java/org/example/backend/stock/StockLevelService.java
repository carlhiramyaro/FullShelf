package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

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

    public StockLevelService(ProductRepository productRepository, StockBalanceService stockBalanceService) {
        this.productRepository = productRepository;
        this.stockBalanceService = stockBalanceService;
    }

    public List<ProductStockLevel> listLevels() {
        return productRepository.findAllByOrderByName().stream()
                .filter(Product::isActive)
                .map(product -> {
                    BigDecimal balance = stockBalanceService.currentBalance(product.getId());
                    return new ProductStockLevel(product, balance, bucket(balance, product.getAlertLevel()));
                })
                .toList();
    }

    private StockLevelStatus bucket(BigDecimal balance, BigDecimal alertLevel) {
        if (balance.signum() < 0) {
            return StockLevelStatus.NEGATIVE;
        }
        if (balance.compareTo(alertLevel) <= 0) {
            return StockLevelStatus.LOW;
        }
        return StockLevelStatus.OK;
    }

    public record ProductStockLevel(Product product, BigDecimal currentBalance, StockLevelStatus status) {
    }
}
