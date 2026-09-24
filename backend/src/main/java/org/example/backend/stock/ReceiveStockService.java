package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Owner-only, per-delivery entry of actual received stock. Unlike opening
 * stock, this is deliberately repeatable — a product can be received any
 * number of times, so there is no "already done" guard. The write itself is
 * a plain StockMovementType.RECEIVED row via StockMovementService.record;
 * this service adds validation and the nominal-vs-actual gap mvp.md asks for
 * (e.g. "10 cartons × 10 kg against 97.6 kg actual, which is 2.4 kg short").
 */
@Service
public class ReceiveStockService {

    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;
    private final StockBalanceService stockBalanceService;

    public ReceiveStockService(ProductRepository productRepository,
                                StockMovementService stockMovementService,
                                StockBalanceService stockBalanceService) {
        this.productRepository = productRepository;
        this.stockMovementService = stockMovementService;
        this.stockBalanceService = stockBalanceService;
    }

    public List<ProductReceiveStatus> listStatus() {
        return productRepository.findAllByOrderByName().stream()
                .filter(Product::isActive)
                .map(product -> new ProductReceiveStatus(product, stockBalanceService.currentBalance(product.getId())))
                .toList();
    }

    @Transactional
    public ReceiveResult receive(Long productId, Integer cartonCount, BigDecimal actualQuantity, User owner) {
        Product product = getProduct(productId);
        validateActualQuantity(product, actualQuantity);
        validateCartonCount(product, cartonCount);

        BigDecimal nominal = null;
        BigDecimal gap = null;
        if (cartonCount != null && product.getCartonWeight() != null) {
            nominal = product.getCartonWeight().multiply(BigDecimal.valueOf(cartonCount));
            gap = actualQuantity.subtract(nominal);
        }

        StockMovement movement = stockMovementService.record(
                product, actualQuantity, StockMovementType.RECEIVED, owner, null, buildNote(cartonCount, nominal, gap));

        return new ReceiveResult(movement, nominal, gap);
    }

    private Product getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No product " + id));
        if (!product.isActive()) {
            throw new IllegalArgumentException("\"" + product.getName() + "\" is not active");
        }
        return product;
    }

    private void validateActualQuantity(Product product, BigDecimal actualQuantity) {
        if (actualQuantity == null || actualQuantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Actual quantity for \"" + product.getName() + "\" must be greater than zero");
        }
        if (actualQuantity.scale() > 2) {
            throw new IllegalArgumentException(
                    "Actual quantity for \"" + product.getName() + "\" can have at most 2 decimal places");
        }
    }

    private void validateCartonCount(Product product, Integer cartonCount) {
        if (cartonCount != null && cartonCount <= 0) {
            throw new IllegalArgumentException(
                    "Carton count for \"" + product.getName() + "\" must be greater than zero");
        }
    }

    private String buildNote(Integer cartonCount, BigDecimal nominal, BigDecimal gap) {
        if (cartonCount == null) {
            return null;
        }
        if (nominal == null) {
            return cartonCount + " carton(s) — no carton weight set for this product, gap not calculated";
        }
        return cartonCount + " carton(s), nominal " + nominal + ", gap " + gap;
    }

    public record ProductReceiveStatus(Product product, BigDecimal currentBalance) {
    }

    public record ReceiveResult(StockMovement movement, BigDecimal nominal, BigDecimal gap) {
    }
}
