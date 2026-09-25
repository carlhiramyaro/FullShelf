package org.example.backend.dashboard;

import jakarta.persistence.EntityManager;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.sale.SaleQueryService;
import org.example.backend.sale.SaleService;
import org.example.backend.sale.VoidSaleService;
import org.example.backend.stock.StockLevelStatus;
import org.example.backend.stock.StockMovementService;
import org.example.backend.stock.StockMovementType;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real Postgres, like SaleQueryIntegrationTest and
 * StockLevelIntegrationTest, wrapped in @Transactional so nothing here is
 * ever committed. Product/staff names are prefixed "Dashboard Test..." for
 * the same real-dev-data collision reason noted throughout this app's tests.
 */
@SpringBootTest
@Transactional
class DashboardIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private VoidSaleService voidSaleService;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void sumsTodaysSalesAndDiscountsExcludingAVoidedSaleAndAnOlderSale() {
        // The totals query sums across *all* of today's sales, not just this
        // test's own product, unlike StockLevelService's per-product lookup
        // — so this asserts the delta the test itself causes, the same way
        // it would need to against a dev DB that already has real sales
        // keyed today (the pre-existing-dev-data issue OpeningStockService's
        // own decision doc already flags for this app's tests).
        SaleQueryService.SaleTotals before = dashboardService.summary().totals();

        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        User staff = userRepository.save(new User("Dashboard Test Staff", UserRole.STAFF));
        Product oil = productRepository.save(new Product(
                "Dashboard Test Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        // Counted: 2 units at 25.00 with a 5.00 discount -> lineTotal 45.00
        SaleService.SaleResult counted = saleService.confirmSale(List.of(
                new SaleService.LineRequest(oil.getId(), new BigDecimal("2"), new BigDecimal("5.00"))
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();

        // Voided: must not count toward either total
        SaleService.SaleResult voided = saleService.confirmSale(List.of(
                new SaleService.LineRequest(oil.getId(), new BigDecimal("1"), null)
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();
        User reloadedOwner = userRepository.findById(owner.getId()).orElseThrow();
        voidSaleService.voidSale(voided.receiptNumber(), null, reloadedOwner);
        entityManager.flush();
        entityManager.clear();

        // Older: backdated to yesterday, must not count toward "today"
        SaleService.SaleResult older = saleService.confirmSale(List.of(
                new SaleService.LineRequest(oil.getId(), new BigDecimal("3"), null)
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("update sales set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), older.sale().getId());
        entityManager.clear();

        SaleQueryService.SaleTotals after = dashboardService.summary().totals();

        assertThat(after.expectedCash().subtract(before.expectedCash())).isEqualByComparingTo("45.00");
        assertThat(after.discounts().subtract(before.discounts())).isEqualByComparingTo("5.00");
        assertThat(after.grossSales().subtract(before.grossSales())).isEqualByComparingTo("50.00");
    }

    @Test
    void splitsLowAndNegativeStockFromStockLevelService() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product low = productRepository.save(new Product(
                "Dashboard Test Low Product", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        stockMovementService.record(low, new BigDecimal("5.00"), StockMovementType.OPENING, owner, null, null);

        Product negative = productRepository.save(new Product(
                "Dashboard Test Negative Product", ProductUnit.UNIT, new BigDecimal("10.00"), new BigDecimal("5.00")));
        stockMovementService.record(negative, new BigDecimal("2.00"), StockMovementType.OPENING, owner, null, null);
        stockMovementService.record(negative, new BigDecimal("-9.00"), StockMovementType.SALE, owner, null, null);

        Product ok = productRepository.save(new Product(
                "Dashboard Test Ok Product", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("5.00")));
        stockMovementService.record(ok, new BigDecimal("50.00"), StockMovementType.OPENING, owner, null, null);

        DashboardService.DashboardSummary summary = dashboardService.summary();

        assertThat(summary.lowStock()).extracting(l -> l.product().getId()).contains(low.getId())
                .doesNotContain(negative.getId(), ok.getId());
        assertThat(summary.negativeStock()).extracting(l -> l.product().getId()).contains(negative.getId())
                .doesNotContain(low.getId(), ok.getId());
        assertThat(summary.negativeStock()).allMatch(l -> l.status() == StockLevelStatus.NEGATIVE);
        assertThat(summary.lowStock()).allMatch(l -> l.status() == StockLevelStatus.LOW);
    }
}
