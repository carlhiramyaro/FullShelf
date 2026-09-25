package org.example.backend.sale;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.stock.StockMovementService;
import org.example.backend.stock.StockMovementType;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The first caller of StockMovementService.record with StockMovementType.SALE
 * — SALE and its sale_line_id link have existed since Phase A, waiting for
 * this slice. No balance check anywhere here: mvp.md explicitly allows
 * selling past zero, and flagging it is Phase F's dashboard reading the
 * ledger after the fact, the same treatment already given to write-off's
 * "no negative-balance guard."
 */
@Service
public class SaleService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final SaleLineRepository saleLineRepository;
    private final StockMovementService stockMovementService;

    public SaleService(ProductRepository productRepository, UserRepository userRepository,
                        SaleRepository saleRepository, SaleLineRepository saleLineRepository,
                        StockMovementService stockMovementService) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.saleRepository = saleRepository;
        this.saleLineRepository = saleLineRepository;
        this.stockMovementService = stockMovementService;
    }

    public List<Product> listSaleableProducts() {
        return productRepository.findAllByOrderByName().stream().filter(Product::isActive).toList();
    }

    @Transactional
    public SaleResult confirmSale(List<LineRequest> lines, Long staffUserId) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A sale needs at least one line");
        }
        User staff = userRepository.findById(staffUserId).orElseThrow();

        // Resolve and validate every line before writing anything — a sale is
        // one atomic confirm action, so one bad line rejects the whole
        // request instead of silently dropping it (unlike Opening Stock's
        // batch, where a blank line is just skipped).
        List<ResolvedLine> resolvedLines = lines.stream().map(this::resolveLine).toList();

        Sale sale = saleRepository.save(new Sale(staff));

        List<LineResult> lineResults = new ArrayList<>();
        for (ResolvedLine resolved : resolvedLines) {
            SaleLine saleLine = saleLineRepository.save(new SaleLine(sale, resolved.product(), resolved.quantity(),
                    resolved.unitPrice(), resolved.discount(), resolved.lineTotal()));
            stockMovementService.record(resolved.product(), resolved.quantity().negate(), StockMovementType.SALE,
                    staff, saleLine, null);
            lineResults.add(new LineResult(resolved.product(), resolved.quantity(), resolved.unitPrice(),
                    resolved.discount(), resolved.lineTotal()));
        }

        saleRepository.flush();
        Long receiptNumber = saleRepository.findReceiptNumberById(sale.getId());
        BigDecimal total = lineResults.stream().map(LineResult::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SaleResult(sale, staff, receiptNumber, lineResults, total);
    }

    private ResolvedLine resolveLine(LineRequest line) {
        Product product = getProduct(line.productId());
        BigDecimal quantity = validateQuantity(product, line.quantity());
        BigDecimal unitPrice = product.getPrice();
        BigDecimal rawTotal = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = validateDiscount(product, line.discount(), rawTotal);
        BigDecimal lineTotal = rawTotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new ResolvedLine(product, quantity, unitPrice, discount, lineTotal);
    }

    private Product getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No product " + id));
        if (!product.isActive()) {
            throw new IllegalArgumentException("\"" + product.getName() + "\" is not available for sale");
        }
        return product;
    }

    // Quantity precision depends on the product's unit: KG allows up to 2
    // decimals (mvp.md's kg buttons + free entry, including 0.5 kg), UNIT
    // requires a whole number since the sales screen only offers +/- there.
    private BigDecimal validateQuantity(Product product, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity for \"" + product.getName() + "\" must be greater than zero");
        }
        if (product.getUnit() == ProductUnit.UNIT) {
            if (quantity.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("\"" + product.getName() + "\" is sold in whole units");
            }
        } else if (quantity.scale() > 2) {
            throw new IllegalArgumentException(
                    "Quantity for \"" + product.getName() + "\" can have at most 2 decimal places");
        }
        return quantity;
    }

    // "Capped at line total, no GH₵ cap" per mvp.md.
    private BigDecimal validateDiscount(Product product, BigDecimal discount, BigDecimal rawTotal) {
        BigDecimal effective = discount == null ? BigDecimal.ZERO : discount;
        if (effective.signum() < 0) {
            throw new IllegalArgumentException("Discount for \"" + product.getName() + "\" cannot be negative");
        }
        if (effective.scale() > 2) {
            throw new IllegalArgumentException(
                    "Discount for \"" + product.getName() + "\" can have at most 2 decimal places");
        }
        if (effective.compareTo(rawTotal) > 0) {
            throw new IllegalArgumentException(
                    "Discount for \"" + product.getName() + "\" cannot exceed the line total");
        }
        return effective;
    }

    private record ResolvedLine(Product product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount,
                                 BigDecimal lineTotal) {
    }

    public record LineRequest(Long productId, BigDecimal quantity, BigDecimal discount) {
    }

    public record LineResult(Product product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount,
                              BigDecimal lineTotal) {
    }

    public record SaleResult(Sale sale, User staff, Long receiptNumber, List<LineResult> lines, BigDecimal total) {
    }
}
