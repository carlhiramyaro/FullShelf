package org.example.backend.product;

import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The only writer of price history. It compares the product's price and cost with its latest
 * history row and writes a new row only when one of them differs, so callers just say "I may
 * have changed this product" and an unchanged save leaves no noise. Called at the end of
 * create, update and Receive stock, after the product's new values are set.
 */
@Service
public class ProductPriceHistoryService {

    private final ProductPriceHistoryRepository repository;

    public ProductPriceHistoryService(ProductPriceHistoryRepository repository) {
        this.repository = repository;
    }

    /** {@code changedBy} may be null (callers with no user, such as older tests); the row is still written. */
    @Transactional
    public void recordIfChanged(Product product, User changedBy) {
        boolean changed = repository.findFirstByProductOrderByChangedAtDescIdDesc(product)
                .map(last -> !same(last.getPrice(), product.getPrice()) || !same(last.getCostPrice(), product.getCostPrice()))
                .orElse(true);
        if (changed) {
            repository.save(new ProductPriceHistory(product, changedBy));
        }
    }

    // 12.5 and 12.50 are the same price; null (no cost) only equals null.
    private static boolean same(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }
}
