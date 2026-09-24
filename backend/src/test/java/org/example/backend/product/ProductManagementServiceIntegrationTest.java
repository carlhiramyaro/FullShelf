package org.example.backend.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres, per this project's convention (see
 * StockLedgerIntegrationTest) — wrapped in @Transactional so nothing written
 * here is ever committed.
 */
@SpringBootTest
@Transactional
class ProductManagementServiceIntegrationTest {

    @Autowired
    private ProductManagementService productManagementService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void createProductPersistsAllFields() {
        Product product = productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), new BigDecimal("10.00"));

        assertThat(product.getId()).isNotNull();
        assertThat(product.isActive()).isTrue();
        assertThat(productRepository.findById(product.getId())).isPresent();
    }

    @Test
    void cartonWeightIsOptional() {
        Product product = productManagementService.createProduct(
                "Tinned tomato", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("20.00"), null);

        assertThat(product.getCartonWeight()).isNull();
    }

    @Test
    void duplicateNameIsRejectedRegardlessOfCase() {
        productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null);

        assertThatThrownBy(() -> productManagementService.createProduct(
                "chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidFieldsAreRejected() {
        assertThatThrownBy(() -> productManagementService.createProduct(
                " ", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> productManagementService.createProduct(
                "Chicken", ProductUnit.KG, BigDecimal.ZERO, new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("-1"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateMutatesFields() {
        Product product = productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null);

        Product updated = productManagementService.updateProduct(
                product.getId(), "Chicken", ProductUnit.KG, new BigDecimal("40.00"), new BigDecimal("8.00"),
                new BigDecimal("10.00"));

        assertThat(updated.getPrice()).isEqualByComparingTo("40.00");
        assertThat(updated.getAlertLevel()).isEqualByComparingTo("8.00");
        assertThat(updated.getCartonWeight()).isEqualByComparingTo("10.00");
    }

    @Test
    void updateKeepingItsOwnNameIsAllowed() {
        Product product = productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null);

        Product updated = productManagementService.updateProduct(
                product.getId(), "Chicken", ProductUnit.KG, new BigDecimal("36.00"), new BigDecimal("10.00"), null);

        assertThat(updated.getName()).isEqualTo("Chicken");
    }

    @Test
    void updateRejectsCollidingWithAnotherProductsName() {
        productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null);
        Product tomato = productManagementService.createProduct(
                "Tinned tomato", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("20.00"), null);

        assertThatThrownBy(() -> productManagementService.updateProduct(
                tomato.getId(), "Chicken", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("20.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivatingProductFlipsActiveFalse() {
        Product product = productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null);

        productManagementService.deactivate(product.getId());

        assertThat(productRepository.findById(product.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void listProductsIsOrderedByName() {
        productManagementService.createProduct(
                "Tinned tomato", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("20.00"), null);
        productManagementService.createProduct(
                "Chicken", ProductUnit.KG, new BigDecimal("35.00"), new BigDecimal("10.00"), null);

        assertThat(productManagementService.listProducts())
                .extracting(Product::getName)
                .containsExactly("Chicken", "Tinned tomato");
    }
}
