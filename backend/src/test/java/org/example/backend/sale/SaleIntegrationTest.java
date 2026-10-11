package org.example.backend.sale;

import org.example.backend.shop.CurrentShop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.example.backend.shop.TestShops;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.stock.StockBalanceService;
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
 * Runs against the real Postgres, like ReceiveStockIntegrationTest and
 * WriteOffIntegrationTest. Wrapped in @Transactional so nothing written here
 * is ever committed. Product names are prefixed "Sale Test..." to avoid the
 * same real-dev-data collision noted in OpeningStockIntegrationTest.
 */
@SpringBootTest
@Transactional
class SaleIntegrationTest {

    @Autowired
    private CurrentShop currentShop;

    @BeforeEach
    void setCurrentShop() {
        currentShop.set(TestShops.ONE);
    }

    @AfterEach
    void clearCurrentShop() {
        currentShop.clear();
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private StockBalanceService stockBalanceService;

    @Test
    void confirmsAMultiLineSaleAndWritesTheLedger() {
        User staff = userRepository.save(new User(TestShops.ONE, "Sale Test Staff", UserRole.STAFF));
        Product chicken = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        Product oil = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        SaleService.SaleResult result = saleService.confirmSale(List.of(
                new SaleService.LineRequest(chicken.getId(), new BigDecimal("1.5"), new BigDecimal("5.00")),
                new SaleService.LineRequest(oil.getId(), new BigDecimal("2"), null)
        ), staff.getId());

        assertThat(result.receiptNumber()).isNotNull();
        assertThat(result.staff().getId()).isEqualTo(staff.getId());
        // 1.5 * 45.00 - 5.00 = 62.50, plus 2 * 25.00 = 50.00
        assertThat(result.total()).isEqualByComparingTo("112.50");

        assertThat(stockBalanceService.currentBalance(chicken.getId())).isEqualByComparingTo("-1.5");
        assertThat(stockBalanceService.currentBalance(oil.getId())).isEqualByComparingTo("-2");
    }

    @Test
    void allowsSellingPastZero() {
        User staff = userRepository.save(new User(TestShops.ONE, "Sale Test Staff", UserRole.STAFF));
        Product chicken = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        SaleService.SaleResult result = saleService.confirmSale(
                List.of(new SaleService.LineRequest(chicken.getId(), new BigDecimal("5"), null)), staff.getId());

        assertThat(result.total()).isEqualByComparingTo("225.00");
        assertThat(stockBalanceService.currentBalance(chicken.getId())).isEqualByComparingTo("-5");
    }

    @Test
    void rejectsAnEmptySale() {
        User staff = userRepository.save(new User(TestShops.ONE, "Sale Test Staff", UserRole.STAFF));

        assertThatThrownBy(() -> saleService.confirmSale(List.of(), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> saleService.confirmSale(null, staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAZeroOrNegativeQuantity() {
        User staff = userRepository.save(new User(TestShops.ONE, "Sale Test Staff", UserRole.STAFF));
        Product chicken = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> saleService.confirmSale(
                List.of(new SaleService.LineRequest(chicken.getId(), BigDecimal.ZERO, null)), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> saleService.confirmSale(
                List.of(new SaleService.LineRequest(chicken.getId(), new BigDecimal("-1"), null)), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAFractionalQuantityForAUnitProduct() {
        User staff = userRepository.save(new User(TestShops.ONE, "Sale Test Staff", UserRole.STAFF));
        Product oil = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        assertThatThrownBy(() -> saleService.confirmSale(
                List.of(new SaleService.LineRequest(oil.getId(), new BigDecimal("1.5"), null)), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole units");
    }

    @Test
    void rejectsADiscountThatExceedsTheLineTotal() {
        User staff = userRepository.save(new User(TestShops.ONE, "Sale Test Staff", UserRole.STAFF));
        Product chicken = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        assertThatThrownBy(() -> saleService.confirmSale(List.of(
                new SaleService.LineRequest(chicken.getId(), BigDecimal.ONE, new BigDecimal("50.00"))
        ), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line total");
    }

    @Test
    void rejectsAnInactiveProduct() {
        User staff = userRepository.save(new User(TestShops.ONE, "Sale Test Staff", UserRole.STAFF));
        Product discontinued = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Discontinued", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));
        discontinued.setActive(false);
        productRepository.save(discontinued);

        assertThatThrownBy(() -> saleService.confirmSale(
                List.of(new SaleService.LineRequest(discontinued.getId(), BigDecimal.ONE, null)), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available for sale");
    }

    @Test
    void listsOnlyActiveProductsForSale() {
        Product active = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Active", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));
        Product inactive = productRepository.save(new Product(TestShops.ONE,
                "Sale Test Inactive", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));
        inactive.setActive(false);
        productRepository.save(inactive);

        List<Long> ids = saleService.listSaleableProducts().stream().map(Product::getId).toList();

        assertThat(ids).contains(active.getId()).doesNotContain(inactive.getId());
    }
}
