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
 * ReceiveStockIntegrationTest. Wrapped in @Transactional so nothing written
 * here is ever committed.
 */
@SpringBootTest
@Transactional
class WriteOffIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WriteOffService writeOffService;

    @Test
    void recordsAWriteOffWithNote() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "WriteOff Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        StockMovement movement = writeOffService.writeOff(
                chicken.getId(), new BigDecimal("2.50"), "Spoiled overnight", owner);

        assertThat(movement.getQuantity()).isEqualByComparingTo("-2.50");
        assertThat(movement.getNote()).isEqualTo("Spoiled overnight");
        assertThat(movement.getType()).isEqualTo(StockMovementType.WRITE_OFF);

        assertThat(writeOffService.listStatus())
                .filteredOn(s -> s.product().getId().equals(chicken.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.currentBalance()).isEqualByComparingTo("-2.50"));
    }

    @Test
    void allowsMultipleWriteOffsOnTheSameProduct() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product tinnedTomato = productRepository.save(new Product(
                "WriteOff Test Tinned Tomato", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("15.00")));

        writeOffService.writeOff(tinnedTomato.getId(), new BigDecimal("3"), "Dented cans", owner);
        writeOffService.writeOff(tinnedTomato.getId(), new BigDecimal("2"), "Damaged in delivery", owner);

        assertThat(writeOffService.listStatus())
                .filteredOn(s -> s.product().getId().equals(tinnedTomato.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.currentBalance()).isEqualByComparingTo("-5"));
    }

    @Test
    void rejectsAZeroOrNegativeQuantity() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "WriteOff Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> writeOffService.writeOff(chicken.getId(), BigDecimal.ZERO, "note", owner))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writeOffService.writeOff(chicken.getId(), new BigDecimal("-5"), "note", owner))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "WriteOff Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> writeOffService.writeOff(chicken.getId(), new BigDecimal("1.234"), "note", owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 decimal places");
    }

    @Test
    void rejectsABlankOrMissingNote() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "WriteOff Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> writeOffService.writeOff(chicken.getId(), new BigDecimal("1"), "  ", owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note is required");
        assertThatThrownBy(() -> writeOffService.writeOff(chicken.getId(), new BigDecimal("1"), null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note is required");
    }

    @Test
    void rejectsAnInactiveProduct() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product discontinued = productRepository.save(new Product(
                "WriteOff Test Discontinued", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));
        discontinued.setActive(false);
        productRepository.save(discontinued);

        assertThatThrownBy(() -> writeOffService.writeOff(discontinued.getId(), new BigDecimal("1"), "note", owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }
}
