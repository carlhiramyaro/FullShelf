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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real Postgres, like SaleIntegrationTest, wrapped in
 * @Transactional so nothing here is ever committed. Product/staff names are
 * prefixed "Sale Query Test..." for the same real-dev-data collision reason
 * noted in OpeningStockIntegrationTest.
 *
 * Every test flushes and clears the persistence context after writing a sale
 * and before querying it back. In real use the write (POST /api/staff/sales)
 * and the read (a later GET) are always separate requests with separate
 * persistence contexts, so the read query hits the DB fresh. Here, though,
 * @Transactional keeps everything in one context — without clearing it,
 * Hibernate's identity map would hand back the very same managed Sale
 * instance the write produced, whose receiptNumber is still null (it's
 * DB-assigned; see SaleRepository.findReceiptNumberById) and whose
 * createdAt still shows the pre-backdate value. Clearing makes the test
 * actually exercise SaleRepository's derived queries against the database.
 */
@SpringBootTest
@Transactional
class SaleQueryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleQueryService saleQueryService;

    // Sale.createdAt has no setter (only @PrePersist sets it once) — this is
    // the one place a test needs to backdate a row, to prove "today" actually
    // excludes yesterday's sales rather than just happening to include
    // everything created during the test run.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void listsTodaysSaleForBothRolesAndReturnsItsDetail() {
        User staff = userRepository.save(new User("Sale Query Test Staff", UserRole.STAFF));
        Product chicken = productRepository.save(new Product(
                "Sale Query Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));

        SaleService.SaleResult result = saleService.confirmSale(List.of(
                new SaleService.LineRequest(chicken.getId(), new BigDecimal("2"), new BigDecimal("5.00"))
        ), staff.getId());
        entityManager.flush();
        entityManager.clear();

        List<SaleQueryService.SaleSummary> today = saleQueryService.listToday();
        assertThat(today).anySatisfy(summary -> {
            assertThat(summary.receiptNumber()).isEqualTo(result.receiptNumber());
            assertThat(summary.staffName()).isEqualTo("Sale Query Test Staff");
            assertThat(summary.total()).isEqualByComparingTo("85.00");
            assertThat(summary.voided()).isFalse();
        });

        List<SaleQueryService.SaleSummary> all = saleQueryService.listAll();
        assertThat(all).anySatisfy(summary -> assertThat(summary.receiptNumber()).isEqualTo(result.receiptNumber()));

        SaleQueryService.SaleDetail detail = saleQueryService.findByReceiptNumber(result.receiptNumber());
        assertThat(detail.staffName()).isEqualTo("Sale Query Test Staff");
        assertThat(detail.total()).isEqualByComparingTo("85.00");
        assertThat(detail.lines()).hasSize(1);
        SaleQueryService.SaleLineDetail line = detail.lines().get(0);
        assertThat(line.productName()).isEqualTo("Sale Query Test Chicken");
        assertThat(line.quantity()).isEqualByComparingTo("2");
        assertThat(line.unitPrice()).isEqualByComparingTo("45.00");
        assertThat(line.discount()).isEqualByComparingTo("5.00");
        assertThat(line.lineTotal()).isEqualByComparingTo("85.00");
    }

    @Test
    void excludesAnOlderSaleFromTodayButNotFromListAll() {
        User staff = userRepository.save(new User("Sale Query Test Staff", UserRole.STAFF));
        Product oil = productRepository.save(new Product(
                "Sale Query Test Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));

        SaleService.SaleResult result = saleService.confirmSale(
                List.of(new SaleService.LineRequest(oil.getId(), BigDecimal.ONE, null)), staff.getId());
        entityManager.flush();
        entityManager.clear();

        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        jdbcTemplate.update("update sales set created_at = ? where id = ?",
                Timestamp.from(yesterday), result.sale().getId());
        entityManager.clear();

        assertThat(saleQueryService.listToday())
                .noneMatch(summary -> summary.receiptNumber().equals(result.receiptNumber()));
        assertThat(saleQueryService.listAll())
                .anyMatch(summary -> summary.receiptNumber().equals(result.receiptNumber()));
    }

    @Test
    void rejectsAnUnknownReceiptNumber() {
        assertThatThrownBy(() -> saleQueryService.findByReceiptNumber(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No sale with receipt number");
    }
}
