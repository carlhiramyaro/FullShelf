package org.example.backend.product;

import org.example.backend.sale.SaleLineRepository;
import org.example.backend.stock.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The only place a product's cost is written, so the rules can't differ between the
 * Products form and Receive stock. A cost can be changed but never cleared, and the
 * first time it goes from none to a value, the product's old sale lines and
 * write-offs that have no cost take it (flagged estimated), once, in this same
 * transaction. After that no line is rewritten, so past profit never moves.
 */
@Service
public class ProductCostService {

    private static final BigDecimal MAX_COST = new BigDecimal("9999999999.99");

    private final SaleLineRepository saleLineRepository;
    private final StockMovementRepository stockMovementRepository;

    public ProductCostService(SaleLineRepository saleLineRepository,
                              StockMovementRepository stockMovementRepository) {
        this.saleLineRepository = saleLineRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    /**
     * {@code newCost == null} means "leave the cost as it is", which keeps a client that
     * doesn't send the field (an old frontend mid-deploy) from clearing or erroring.
     * The caller has already loaded the product with the caller's shop.
     */
    @Transactional
    public void applyCost(Product product, BigDecimal newCost) {
        if (newCost == null) {
            return;
        }
        validate(product, newCost);
        boolean firstCost = product.getCostPrice() == null;
        product.setCostPrice(newCost);
        if (firstCost) {
            saleLineRepository.fillMissingCost(product, newCost);
            stockMovementRepository.fillMissingWriteOffCost(product, newCost);
        }
    }

    private void validate(Product product, BigDecimal cost) {
        if (cost.signum() < 0) {
            throw new IllegalArgumentException("Cost for \"" + product.getName() + "\" cannot be negative");
        }
        if (cost.scale() > 2) {
            throw new IllegalArgumentException(
                    "Cost for \"" + product.getName() + "\" can have at most 2 decimal places");
        }
        if (cost.compareTo(MAX_COST) > 0) {
            throw new IllegalArgumentException("Cost for \"" + product.getName() + "\" is too large");
        }
    }
}
