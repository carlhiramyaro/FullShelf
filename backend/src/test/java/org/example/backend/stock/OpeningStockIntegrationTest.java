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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real Postgres configured via application.properties, like
 * StockLedgerIntegrationTest. Wrapped in @Transactional so nothing written
 * here is ever committed.
 */
@SpringBootTest
@Transactional
class OpeningStockIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private OpeningStockService openingStockService;

    @Test
    void recordsOpeningBalanceForAFreshProduct() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(
                new Product("Opening Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        openingStockService.recordOpeningBalances(Map.of(chicken.getId(), new BigDecimal("42.50")), owner);

        List<OpeningStockService.ProductOpeningStatus> status = openingStockService.listStatus();
        assertThat(status)
                .filteredOn(s -> s.product().getId().equals(chicken.getId()))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.alreadySet()).isTrue();
                    assertThat(s.currentBalance()).isEqualByComparingTo("42.50");
                });
    }

    @Test
    void rejectsASecondOpeningEntryForTheSameProduct() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product oil = productRepository.save(
                new Product("Opening Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        openingStockService.recordOpeningBalances(Map.of(oil.getId(), new BigDecimal("60")), owner);

        assertThatThrownBy(() ->
                openingStockService.recordOpeningBalances(Map.of(oil.getId(), new BigDecimal("60")), owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been set");
    }

    @Test
    void allowsReenteringAfterTheOriginalOpeningEntryWasReversed() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product tinnedTomato = productRepository.save(
                new Product("Opening Test Tinned Tomato", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("15.00")));

        StockMovement mistyped = stockMovementService.record(
                tinnedTomato, new BigDecimal("1000.00"), StockMovementType.OPENING, owner, null, null);
        stockMovementService.reverse(mistyped, StockMovementType.REVERSAL, owner, "mistyped, meant 100");

        openingStockService.recordOpeningBalances(Map.of(tinnedTomato.getId(), new BigDecimal("100.00")), owner);

        List<OpeningStockService.ProductOpeningStatus> status = openingStockService.listStatus();
        assertThat(status)
                .filteredOn(s -> s.product().getId().equals(tinnedTomato.getId()))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.alreadySet()).isTrue();
                    assertThat(s.currentBalance()).isEqualByComparingTo("100.00");
                });
    }

    @Test
    void rejectsANegativeQuantityAndWritesNothingElseInTheBatch() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(
                new Product("Opening Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        Product oil = productRepository.save(
                new Product("Opening Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        assertThatThrownBy(() -> openingStockService.recordOpeningBalances(
                Map.of(chicken.getId(), new BigDecimal("42.50"), oil.getId(), new BigDecimal("-1")), owner))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(openingStockService.listStatus())
                .filteredOn(s -> s.product().getId().equals(chicken.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.alreadySet()).isFalse());
    }
}
