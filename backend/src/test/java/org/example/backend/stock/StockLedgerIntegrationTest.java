package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real Postgres configured via application.properties (a
 * local dev database, or DATABASE_URL/USERNAME/PASSWORD in CI), so Flyway's
 * migration and Hibernate's entity mappings (ddl-auto=validate) are checked
 * against the actual engine, not an approximation. Wrapped in @Transactional
 * so nothing written here is ever committed.
 */
@SpringBootTest
@Transactional
class StockLedgerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private StockBalanceService stockBalanceService;

    @Test
    void balanceReflectsOpeningReceivedSaleAndWriteOff() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(
                new Product("Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        stockMovementService.record(chicken, new BigDecimal("42.50"), StockMovementType.OPENING, owner, null, null);
        stockMovementService.record(chicken, new BigDecimal("20.00"), StockMovementType.RECEIVED, owner, null, null);
        stockMovementService.record(chicken, new BigDecimal("-5.50"), StockMovementType.SALE, owner, null, null);
        stockMovementService.record(chicken, new BigDecimal("-1.00"), StockMovementType.WRITE_OFF, owner, null, null);

        assertThat(stockBalanceService.currentBalance(chicken.getId())).isEqualByComparingTo("56.00");
    }

    @Test
    void reversingAMovementNegatesItAndLeavesTheOriginalVisible() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product tinnedTomato = productRepository.save(
                new Product("Tinned tomato", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("15.00")));

        StockMovement mistyped = stockMovementService.record(
                tinnedTomato, new BigDecimal("1000.00"), StockMovementType.OPENING, owner, null, null);

        stockMovementService.reverse(mistyped, StockMovementType.REVERSAL, owner, "mistyped, meant 100");
        stockMovementService.record(tinnedTomato, new BigDecimal("100.00"), StockMovementType.OPENING, owner, null,
                "corrected figure");

        assertThat(stockBalanceService.currentBalance(tinnedTomato.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    void reversingTheSameMovementTwiceIsRejected() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product oil = productRepository.save(
                new Product("Cooking oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        StockMovement received = stockMovementService.record(
                oil, new BigDecimal("50.00"), StockMovementType.RECEIVED, owner, null, null);
        stockMovementService.reverse(received, StockMovementType.REVERSAL, owner, "first reversal");

        assertThatThrownBy(() ->
                stockMovementService.reverse(received, StockMovementType.REVERSAL, owner, "second reversal"))
                .isInstanceOf(IllegalStateException.class);
    }
}
