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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real Postgres configured via application.properties, like
 * OpeningStockIntegrationTest. Wrapped in @Transactional so nothing written
 * here is ever committed. Product names use "Reverse Test..." to avoid the
 * documented dev-DB collision on the case-insensitive name index.
 */
@SpringBootTest
@Transactional
class ReverseStockIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private ReverseStockService reverseStockService;

    @Test
    void reversesAMistypedOpeningLine() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(
                new Product("Reverse Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        StockMovement mistyped = stockMovementService.record(
                chicken, new BigDecimal("1000.00"), StockMovementType.OPENING, owner, null, null);

        StockMovement reversal = reverseStockService.reverseEntry(mistyped.getId(), "mistyped, meant 100", owner);

        assertThat(reversal.getType()).isEqualTo(StockMovementType.REVERSAL);
        assertThat(reversal.getQuantity()).isEqualByComparingTo("-1000.00");
        assertThat(reversal.getReversedMovement().getId()).isEqualTo(mistyped.getId());
        assertThat(reverseStockService.reversibleMovements(chicken.getId())).isEmpty();
    }

    @Test
    void reversesAReceivedLine() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product oil = productRepository.save(
                new Product("Reverse Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));
        StockMovement received = stockMovementService.record(
                oil, new BigDecimal("60.00"), StockMovementType.RECEIVED, owner, null, "6 carton(s)");

        StockMovement reversal = reverseStockService.reverseEntry(received.getId(), null, owner);

        assertThat(reversal.getType()).isEqualTo(StockMovementType.REVERSAL);
        assertThat(reversal.getQuantity()).isEqualByComparingTo("-60.00");
    }

    @Test
    void rejectsReversingTheSameLineTwice() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product tinnedTomato = productRepository.save(
                new Product("Reverse Test Tinned Tomato", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("15.00")));
        StockMovement opening = stockMovementService.record(
                tinnedTomato, new BigDecimal("100.00"), StockMovementType.OPENING, owner, null, null);
        reverseStockService.reverseEntry(opening.getId(), "first reversal", owner);

        assertThatThrownBy(() -> reverseStockService.reverseEntry(opening.getId(), "second attempt", owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been reversed");
    }

    @Test
    void rejectsReversingAWriteOffLine() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product spices = productRepository.save(
                new Product("Reverse Test Spices", ProductUnit.UNIT, new BigDecimal("12.00"), new BigDecimal("5.00")));
        StockMovement writeOff = stockMovementService.record(
                spices, new BigDecimal("-3.00"), StockMovementType.WRITE_OFF, owner, null, "damaged");

        assertThatThrownBy(() -> reverseStockService.reverseEntry(writeOff.getId(), null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("opening or received");
    }

    @Test
    void listsReversibleMovementsAndExcludesAlreadyReversedOnes() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(
                new Product("Reverse Test Beef", ProductUnit.KG, new BigDecimal("50.00"), new BigDecimal("10.00")));
        StockMovement opening = stockMovementService.record(
                chicken, new BigDecimal("20.00"), StockMovementType.OPENING, owner, null, null);
        StockMovement received = stockMovementService.record(
                chicken, new BigDecimal("15.00"), StockMovementType.RECEIVED, owner, null, null);

        assertThat(reverseStockService.reversibleMovements(chicken.getId()))
                .extracting(StockMovement::getId)
                .containsExactlyInAnyOrder(opening.getId(), received.getId());

        reverseStockService.reverseEntry(opening.getId(), null, owner);

        List<StockMovement> remaining = reverseStockService.reversibleMovements(chicken.getId());
        assertThat(remaining).extracting(StockMovement::getId).containsExactly(received.getId());
    }
}
