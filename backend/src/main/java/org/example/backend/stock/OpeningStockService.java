package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owner-only, go-live-only entry of each product's starting balance.
 * Opening stock is a plain StockMovementType.OPENING row, so the write
 * itself is just StockMovementService.record — this service adds the two
 * things that primitive doesn't have on its own: validating every line of a
 * batch before writing any of them, and refusing a second OPENING entry for
 * a product that still has one standing. A mistyped opening figure is fixed
 * by Reverse Entry (which negates the original), not by re-submitting this
 * form — see docs/decisions.md.
 */
@Service
public class OpeningStockService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockMovementService stockMovementService;
    private final StockBalanceService stockBalanceService;

    public OpeningStockService(ProductRepository productRepository,
                                StockMovementRepository stockMovementRepository,
                                StockMovementService stockMovementService,
                                StockBalanceService stockBalanceService) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockMovementService = stockMovementService;
        this.stockBalanceService = stockBalanceService;
    }

    public List<ProductOpeningStatus> listStatus() {
        return productRepository.findAllByOrderByName().stream()
                .filter(Product::isActive)
                .map(product -> new ProductOpeningStatus(product, hasUnreversedOpeningMovement(product.getId()),
                        stockBalanceService.currentBalance(product.getId())))
                .toList();
    }

    @Transactional
    public void recordOpeningBalances(Map<Long, BigDecimal> quantitiesByProductId, User owner) {
        Map<Long, Product> products = quantitiesByProductId.keySet().stream()
                .collect(Collectors.toMap(id -> id, this::getProduct));

        // Validate every line before writing any of them, so a bad line in
        // the batch never leaves some products opened and others not.
        quantitiesByProductId.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            validateQuantity(product, quantity);
            if (hasUnreversedOpeningMovement(productId)) {
                throw new IllegalArgumentException("Opening stock has already been set for \"" + product.getName()
                        + "\" — use Reverse entry to correct it");
            }
        });

        quantitiesByProductId.forEach((productId, quantity) ->
                stockMovementService.record(products.get(productId), quantity, StockMovementType.OPENING, owner,
                        null, null));
    }

    private boolean hasUnreversedOpeningMovement(Long productId) {
        return stockMovementRepository.findByProductIdAndType(productId, StockMovementType.OPENING).stream()
                .anyMatch(movement -> !stockMovementRepository.existsByReversedMovementId(movement.getId()));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No product " + id));
    }

    private void validateQuantity(Product product, BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException(
                    "Opening quantity for \"" + product.getName() + "\" cannot be negative");
        }
        if (quantity.scale() > 2) {
            throw new IllegalArgumentException(
                    "Opening quantity for \"" + product.getName() + "\" can have at most 2 decimal places");
        }
    }

    public record ProductOpeningStatus(Product product, boolean alreadySet, BigDecimal currentBalance) {
    }
}
