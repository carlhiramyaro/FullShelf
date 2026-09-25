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

/**
 * Runs against the real Postgres configured via application.properties, like
 * ReverseStockIntegrationTest. Wrapped in @Transactional so nothing written
 * here is ever committed. Product names use "Level Test..." to avoid the
 * documented dev-DB collision on the case-insensitive name index.
 */
@SpringBootTest
@Transactional
class StockLevelIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private StockLevelService stockLevelService;

    @Test
    void flagsAProductAtOrBelowItsAlertLevelAsLow() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(
                new Product("Level Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        stockMovementService.record(chicken, new BigDecimal("10.00"), StockMovementType.OPENING, owner, null, null);

        StockLevelService.ProductStockLevel level = levelFor(chicken.getId());

        assertThat(level.currentBalance()).isEqualByComparingTo("10.00");
        assertThat(level.status()).isEqualTo(StockLevelStatus.LOW);
    }

    @Test
    void flagsAProductBelowZeroAsNegativeEvenThoughItsAlsoLow() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product oil = productRepository.save(
                new Product("Level Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));
        stockMovementService.record(oil, new BigDecimal("3.00"), StockMovementType.OPENING, owner, null, null);
        stockMovementService.record(oil, new BigDecimal("-8.00"), StockMovementType.SALE, owner, null, null);

        StockLevelService.ProductStockLevel level = levelFor(oil.getId());

        assertThat(level.currentBalance()).isEqualByComparingTo("-5.00");
        assertThat(level.status()).isEqualTo(StockLevelStatus.NEGATIVE);
    }

    @Test
    void flagsAProductComfortablyAboveItsAlertLevelAsOk() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product tinnedTomato = productRepository.save(
                new Product("Level Test Tinned Tomato", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("15.00")));
        stockMovementService.record(
                tinnedTomato, new BigDecimal("60.00"), StockMovementType.OPENING, owner, null, null);

        StockLevelService.ProductStockLevel level = levelFor(tinnedTomato.getId());

        assertThat(level.currentBalance()).isEqualByComparingTo("60.00");
        assertThat(level.status()).isEqualTo(StockLevelStatus.OK);
    }

    @Test
    void excludesInactiveProducts() {
        Product spices = productRepository.save(
                new Product("Level Test Spices", ProductUnit.UNIT, new BigDecimal("12.00"), new BigDecimal("5.00")));
        spices.setActive(false);
        productRepository.save(spices);

        assertThat(stockLevelService.listLevels())
                .extracting(l -> l.product().getId())
                .doesNotContain(spices.getId());
    }

    private StockLevelService.ProductStockLevel levelFor(Long productId) {
        List<StockLevelService.ProductStockLevel> levels = stockLevelService.listLevels();
        return levels.stream()
                .filter(level -> level.product().getId().equals(productId))
                .findFirst()
                .orElseThrow();
    }
}
