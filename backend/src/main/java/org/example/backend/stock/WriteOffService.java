package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Owner-only entry of stock lost outside a sale (spoilage, damage). Like
 * Receive Stock, this is deliberately repeatable — no "already done" guard —
 * and writes a plain StockMovementType.WRITE_OFF row via
 * StockMovementService.record, negated because a write-off decreases stock.
 */
@Service
public class WriteOffService {

    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;
    private final StockBalanceService stockBalanceService;

    public WriteOffService(ProductRepository productRepository,
                            StockMovementService stockMovementService,
                            StockBalanceService stockBalanceService) {
        this.productRepository = productRepository;
        this.stockMovementService = stockMovementService;
        this.stockBalanceService = stockBalanceService;
    }

    public List<ProductWriteOffStatus> listStatus() {
        return productRepository.findAllByOrderByName().stream()
                .filter(Product::isActive)
                .map(product -> new ProductWriteOffStatus(product, stockBalanceService.currentBalance(product.getId())))
                .toList();
    }

    @Transactional
    public StockMovement writeOff(Long productId, BigDecimal quantity, String note, User owner) {
        Product product = getProduct(productId);
        validateQuantity(product, quantity);
        validateNote(product, note);

        return stockMovementService.record(
                product, quantity.negate(), StockMovementType.WRITE_OFF, owner, null, note.trim());
    }

    private Product getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No product " + id));
        if (!product.isActive()) {
            throw new IllegalArgumentException("\"" + product.getName() + "\" is not active");
        }
        return product;
    }

    private void validateQuantity(Product product, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Write-off quantity for \"" + product.getName() + "\" must be greater than zero");
        }
        if (quantity.scale() > 2) {
            throw new IllegalArgumentException(
                    "Write-off quantity for \"" + product.getName() + "\" can have at most 2 decimal places");
        }
    }

    private void validateNote(Product product, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException(
                    "A note is required to write off \"" + product.getName() + "\"");
        }
    }

    public record ProductWriteOffStatus(Product product, BigDecimal currentBalance) {
    }
}
