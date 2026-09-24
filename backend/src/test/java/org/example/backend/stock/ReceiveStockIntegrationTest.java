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
 * Runs against the real Postgres configured via application.properties, like
 * OpeningStockIntegrationTest. Wrapped in @Transactional so nothing written
 * here is ever committed.
 */
@SpringBootTest
@Transactional
class ReceiveStockIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReceiveStockService receiveStockService;

    @Test
    void recordsAReceiptAndComputesAShortGap() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Receive Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        chicken.setCartonWeight(new BigDecimal("10.00"));
        productRepository.save(chicken);

        ReceiveStockService.ReceiveResult result =
                receiveStockService.receive(chicken.getId(), 10, new BigDecimal("97.60"), owner);

        assertThat(result.nominal()).isEqualByComparingTo("100.00");
        assertThat(result.gap()).isEqualByComparingTo("-2.40");

        assertThat(receiveStockService.listStatus())
                .filteredOn(s -> s.product().getId().equals(chicken.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.currentBalance()).isEqualByComparingTo("97.60"));
    }

    @Test
    void recordsAReceiptWithNoCartonCountAndNoGap() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product oil = productRepository.save(new Product(
                "Receive Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        ReceiveStockService.ReceiveResult result =
                receiveStockService.receive(oil.getId(), null, new BigDecimal("24"), owner);

        assertThat(result.nominal()).isNull();
        assertThat(result.gap()).isNull();
        assertThat(receiveStockService.listStatus())
                .filteredOn(s -> s.product().getId().equals(oil.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.currentBalance()).isEqualByComparingTo("24"));
    }

    @Test
    void skipsGapWhenProductHasNoCartonWeightSet() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product tinnedTomato = productRepository.save(new Product(
                "Receive Test Tinned Tomato", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("15.00")));

        ReceiveStockService.ReceiveResult result =
                receiveStockService.receive(tinnedTomato.getId(), 5, new BigDecimal("60"), owner);

        assertThat(result.nominal()).isNull();
        assertThat(result.gap()).isNull();
    }

    @Test
    void allowsReceivingTheSameProductMultipleTimes() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Receive Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        receiveStockService.receive(chicken.getId(), null, new BigDecimal("50.00"), owner);
        receiveStockService.receive(chicken.getId(), null, new BigDecimal("20.00"), owner);

        assertThat(receiveStockService.listStatus())
                .filteredOn(s -> s.product().getId().equals(chicken.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.currentBalance()).isEqualByComparingTo("70.00"));
    }

    @Test
    void rejectsAZeroOrNegativeActualQuantity() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Receive Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> receiveStockService.receive(chicken.getId(), null, BigDecimal.ZERO, owner))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> receiveStockService.receive(chicken.getId(), null, new BigDecimal("-5"), owner))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Receive Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> receiveStockService.receive(chicken.getId(), null, new BigDecimal("1.234"), owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 decimal places");
    }

    @Test
    void rejectsAZeroOrNegativeCartonCount() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Receive Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> receiveStockService.receive(chicken.getId(), 0, new BigDecimal("10"), owner))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> receiveStockService.receive(chicken.getId(), -1, new BigDecimal("10"), owner))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInactiveProduct() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product discontinued = productRepository.save(new Product(
                "Receive Test Discontinued", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));
        discontinued.setActive(false);
        productRepository.save(discontinued);

        assertThatThrownBy(() -> receiveStockService.receive(discontinued.getId(), null, new BigDecimal("10"), owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }
}
