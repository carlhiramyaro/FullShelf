package org.example.backend.sale;

import jakarta.persistence.EntityManager;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real Postgres like SaleQueryIntegrationTest, wrapped in
 * @Transactional so nothing here is ever committed. Product/staff names are
 * prefixed "Day Close Test..." for the same real-dev-data collision reason
 * noted throughout this app's tests.
 *
 * Each test backdates a sale's created_at into the picked day's range with
 * JdbcTemplate, same technique SaleQueryIntegrationTest uses for "yesterday"
 * — Sale.createdAt has no setter beyond its @PrePersist.
 */
@SpringBootTest
@Transactional
class DayCloseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private VoidSaleService voidSaleService;

    @Autowired
    private SaleQueryService saleQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private static final LocalDate PICKED_DAY = LocalDate.of(2026, 3, 10);

    private void backdateTo(Long saleId, LocalDate date) {
        Instant timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
        jdbcTemplate.update("update sales set created_at = ? where id = ?", Timestamp.from(timestamp), saleId);
        entityManager.clear();
    }

    @Test
    void listsAndTotalsOnlyThePickedDaysUnvoidedSales() {
        User staff = userRepository.save(new User("Day Close Test Staff", UserRole.STAFF));
        User owner = userRepository.save(new User("Day Close Test Owner", UserRole.OWNER));
        Product oil = productRepository.save(new Product(
                "Day Close Test Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        // On the picked day, counted: 2 units at 25.00 with a 5.00 discount -> lineTotal 45.00
        SaleService.SaleResult counted = saleService.confirmSale(List.of(
                new SaleService.LineRequest(oil.getId(), new BigDecimal("2"), new BigDecimal("5.00"))
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();
        backdateTo(counted.sale().getId(), PICKED_DAY);

        // On the picked day, but voided: must not count toward totals, but still appears in the list
        SaleService.SaleResult voided = saleService.confirmSale(List.of(
                new SaleService.LineRequest(oil.getId(), new BigDecimal("1"), null)
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();
        backdateTo(voided.sale().getId(), PICKED_DAY);
        User reloadedOwner = userRepository.findById(owner.getId()).orElseThrow();
        voidSaleService.voidSale(voided.receiptNumber(), null, reloadedOwner);
        entityManager.flush();
        entityManager.clear();

        // The day before: must not appear at all
        SaleService.SaleResult dayBefore = saleService.confirmSale(List.of(
                new SaleService.LineRequest(oil.getId(), new BigDecimal("3"), null)
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();
        backdateTo(dayBefore.sale().getId(), PICKED_DAY.minusDays(1));

        // The day after: must not appear at all
        SaleService.SaleResult dayAfter = saleService.confirmSale(List.of(
                new SaleService.LineRequest(oil.getId(), new BigDecimal("4"), null)
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();
        backdateTo(dayAfter.sale().getId(), PICKED_DAY.plusDays(1));

        List<SaleQueryService.DaySaleSummary> sales = saleQueryService.listForDay(PICKED_DAY);
        assertThat(sales).extracting(SaleQueryService.DaySaleSummary::receiptNumber)
                .contains(counted.receiptNumber(), voided.receiptNumber())
                .doesNotContain(dayBefore.receiptNumber(), dayAfter.receiptNumber());

        assertThat(sales).anySatisfy(summary -> {
            assertThat(summary.receiptNumber()).isEqualTo(counted.receiptNumber());
            assertThat(summary.staffName()).isEqualTo("Day Close Test Staff");
            assertThat(summary.total()).isEqualByComparingTo("45.00");
            assertThat(summary.discount()).isEqualByComparingTo("5.00");
            assertThat(summary.voided()).isFalse();
        });
        assertThat(sales).anySatisfy(summary -> {
            assertThat(summary.receiptNumber()).isEqualTo(voided.receiptNumber());
            assertThat(summary.voided()).isTrue();
        });

        SaleQueryService.SaleTotals totals = saleQueryService.dayTotals(PICKED_DAY);
        assertThat(totals.expectedCash()).isEqualByComparingTo("45.00");
        assertThat(totals.discounts()).isEqualByComparingTo("5.00");
        assertThat(totals.grossSales()).isEqualByComparingTo("50.00");
    }

    @Test
    void aDayWithNoSalesHasEmptyListAndZeroTotals() {
        LocalDate emptyDay = LocalDate.of(2020, 1, 1);

        assertThat(saleQueryService.listForDay(emptyDay)).isEmpty();
        SaleQueryService.SaleTotals totals = saleQueryService.dayTotals(emptyDay);
        assertThat(totals.expectedCash()).isEqualByComparingTo("0");
        assertThat(totals.discounts()).isEqualByComparingTo("0");
        assertThat(totals.grossSales()).isEqualByComparingTo("0");
    }
}
