package org.example.backend.sale;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.stock.StockBalanceService;
import org.example.backend.stock.StockMovement;
import org.example.backend.stock.StockMovementRepository;
import org.example.backend.stock.StockMovementType;
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
 * Runs against the real Postgres, like SaleIntegrationTest. Wrapped in
 * @Transactional so nothing written here is ever committed. Product names
 * use "Void Test..." to avoid the documented dev-DB collision on the
 * case-insensitive name index.
 */
@SpringBootTest
@Transactional
class VoidSaleIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private VoidSaleService voidSaleService;

    @Autowired
    private StockBalanceService stockBalanceService;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    void voidsAMultiLineSaleAndRestoresStock() {
        User staff = userRepository.save(new User("Void Test Staff", UserRole.STAFF));
        User owner = userRepository.save(new User("Void Test Owner", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Void Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        Product oil = productRepository.save(new Product(
                "Void Test Cooking Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        SaleService.SaleResult sale = saleService.confirmSale(List.of(
                new SaleService.LineRequest(chicken.getId(), new BigDecimal("1.5"), null),
                new SaleService.LineRequest(oil.getId(), new BigDecimal("2"), null)
        ), staff.getId());
        assertThat(stockBalanceService.currentBalance(chicken.getId())).isEqualByComparingTo("-1.5");
        assertThat(stockBalanceService.currentBalance(oil.getId())).isEqualByComparingTo("-2");

        Sale voided = voidSaleService.voidSale(sale.receiptNumber(), "mistakenly keyed", owner);

        assertThat(voided.isVoided()).isTrue();
        assertThat(stockBalanceService.currentBalance(chicken.getId())).isEqualByComparingTo("0");
        assertThat(stockBalanceService.currentBalance(oil.getId())).isEqualByComparingTo("0");
    }

    @Test
    void leavesTheOriginalSaleMovementsUntouchedAndAddsVoidRows() {
        User staff = userRepository.save(new User("Void Test Staff", UserRole.STAFF));
        User owner = userRepository.save(new User("Void Test Owner", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Void Test Beef", ProductUnit.KG, new BigDecimal("50.00"), new BigDecimal("10.00")));

        SaleService.SaleResult sale = saleService.confirmSale(
                List.of(new SaleService.LineRequest(chicken.getId(), new BigDecimal("3"), null)), staff.getId());

        voidSaleService.voidSale(sale.receiptNumber(), null, owner);

        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtAsc(chicken.getId());
        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).getType()).isEqualTo(StockMovementType.SALE);
        assertThat(movements.get(0).getQuantity()).isEqualByComparingTo("-3");
        assertThat(movements.get(1).getType()).isEqualTo(StockMovementType.VOID);
        assertThat(movements.get(1).getQuantity()).isEqualByComparingTo("3");
        assertThat(movements.get(1).getReversedMovement().getId()).isEqualTo(movements.get(0).getId());
    }

    @Test
    void rejectsVoidingTheSameSaleTwice() {
        User staff = userRepository.save(new User("Void Test Staff", UserRole.STAFF));
        User owner = userRepository.save(new User("Void Test Owner", UserRole.OWNER));
        Product spices = productRepository.save(new Product(
                "Void Test Spices", ProductUnit.UNIT, new BigDecimal("12.00"), new BigDecimal("5.00")));

        SaleService.SaleResult sale = saleService.confirmSale(
                List.of(new SaleService.LineRequest(spices.getId(), BigDecimal.ONE, null)), staff.getId());
        voidSaleService.voidSale(sale.receiptNumber(), null, owner);

        assertThatThrownBy(() -> voidSaleService.voidSale(sale.receiptNumber(), null, owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been voided");
    }

    @Test
    void rejectsVoidingAnUnknownReceiptNumber() {
        User owner = userRepository.save(new User("Void Test Owner", UserRole.OWNER));

        assertThatThrownBy(() -> voidSaleService.voidSale(999_999_999L, null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No sale with receipt number");
    }
}
