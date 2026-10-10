package org.example.backend.shop;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.sale.SaleService;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The V6 guarantees: what used to be unique across the whole database is now
 * unique per shop, and each shop numbers its own receipts. Two shops are
 * created here so overlap between them is actually exercised.
 */
@SpringBootTest
@Transactional
class ShopSchemaIntegrationTest {

    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SaleService saleService;

    private Product product(Shop shop, String name) {
        return new Product(shop, name, ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00"));
    }

    @Test
    void shopOneExistsFromTheMigration() {
        assertThat(shopRepository.findById(1L)).isPresent();
    }

    @Test
    void sameProductNameIsAllowedInTwoShops() {
        Shop a = shopRepository.save(new Shop("Shop A"));
        Shop b = shopRepository.save(new Shop("Shop B"));

        productRepository.saveAndFlush(product(a, "Schema Test Chicken"));
        productRepository.saveAndFlush(product(b, "schema test chicken"));

        assertThat(productRepository.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void sameProductNameIsRejectedWithinOneShop() {
        Shop a = shopRepository.save(new Shop("Shop A"));
        productRepository.saveAndFlush(product(a, "Schema Test Chicken"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product(a, "SCHEMA TEST CHICKEN")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eachShopNumbersItsOwnReceiptsFrom1000() {
        Shop a = shopRepository.saveAndFlush(new Shop("Shop A"));
        Shop b = shopRepository.saveAndFlush(new Shop("Shop B"));

        assertThat(shopRepository.nextReceiptNumber(a.getId())).isEqualTo(1000L);
        assertThat(shopRepository.nextReceiptNumber(a.getId())).isEqualTo(1001L);
        assertThat(shopRepository.nextReceiptNumber(b.getId())).isEqualTo(1000L);
        assertThat(shopRepository.nextReceiptNumber(a.getId())).isEqualTo(1002L);
    }

    @Test
    void twoShopsCanBothSaveReceiptOneThousand() {
        Shop a = shopRepository.saveAndFlush(new Shop("Shop A"));
        Shop b = shopRepository.saveAndFlush(new Shop("Shop B"));
        User staffA = userRepository.save(new User(a, "Schema Staff A", UserRole.STAFF));
        User staffB = userRepository.save(new User(b, "Schema Staff B", UserRole.STAFF));
        Product pa = productRepository.save(product(a, "Schema Test Oil"));
        Product pb = productRepository.save(product(b, "Schema Test Oil"));

        var saleA = saleService.confirmSale(
                List.of(new SaleService.LineRequest(pa.getId(), BigDecimal.ONE, null)), staffA.getId());
        var saleB = saleService.confirmSale(
                List.of(new SaleService.LineRequest(pb.getId(), BigDecimal.ONE, null)), staffB.getId());

        assertThat(saleA.receiptNumber()).isEqualTo(1000L);
        assertThat(saleB.receiptNumber()).isEqualTo(1000L);
    }

    @Test
    void movementShopComesFromItsProduct() {
        Shop a = shopRepository.saveAndFlush(new Shop("Shop A"));
        User staff = userRepository.save(new User(a, "Schema Staff A", UserRole.STAFF));
        Product p = productRepository.save(product(a, "Schema Test Tin"));

        var sale = saleService.confirmSale(
                List.of(new SaleService.LineRequest(p.getId(), BigDecimal.ONE, null)), staff.getId());

        assertThat(sale.sale().getShop().getId()).isEqualTo(a.getId());
    }
}
