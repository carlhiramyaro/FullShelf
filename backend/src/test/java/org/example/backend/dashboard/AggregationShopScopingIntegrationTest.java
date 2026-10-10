package org.example.backend.dashboard;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.sale.SaleQueryService;
import org.example.backend.sale.SaleService;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.shop.TestShops;
import org.example.backend.stock.StockLevelService;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G5 smoke check: sales in another shop must not change this shop's totals,
 * and its products must not appear in this shop's dashboard lists. The full
 * cross-shop suite is G6.
 */
@SpringBootTest
@Transactional
class AggregationShopScopingIntegrationTest {

    @Autowired
    private CurrentShop currentShop;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SaleService saleService;
    @Autowired
    private SaleQueryService saleQueryService;
    @Autowired
    private DashboardService dashboardService;

    private Shop two;
    private User staffTwo;
    private Product productTwo;

    @BeforeEach
    void setUp() {
        two = shopRepository.saveAndFlush(new Shop("Aggregation Shop Two"));
        staffTwo = new User(two, "Aggregation Staff Two", UserRole.STAFF);
        staffTwo.setPinHash("x");
        staffTwo = userRepository.save(staffTwo);
        productTwo = productRepository.save(
                new Product(two, "Aggregation Oil", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));
        currentShop.set(TestShops.ONE);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    @Test
    void anotherShopsSalesDoNotChangeTodayOrDayTotals() {
        SaleQueryService.SaleTotals todayBefore = saleQueryService.todayTotals();
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        SaleQueryService.SaleTotals dayBefore = saleQueryService.dayTotals(today);

        currentShop.set(two);
        saleService.confirmSale(
                List.of(new SaleService.LineRequest(productTwo.getId(), new BigDecimal("3"), new BigDecimal("1.00"))),
                staffTwo.getId());
        assertThat(saleQueryService.todayTotals().expectedCash()).isEqualByComparingTo("14.00");
        assertThat(saleQueryService.todayTotals().discounts()).isEqualByComparingTo("1.00");

        currentShop.set(TestShops.ONE);
        assertThat(saleQueryService.todayTotals()).isEqualTo(todayBefore);
        assertThat(saleQueryService.dayTotals(today)).isEqualTo(dayBefore);
    }

    @Test
    void anotherShopsNegativeStockStaysOutOfThisShopsDashboard() {
        currentShop.set(two);
        saleService.confirmSale(
                List.of(new SaleService.LineRequest(productTwo.getId(), BigDecimal.ONE, BigDecimal.ZERO)),
                staffTwo.getId());
        assertThat(dashboardService.summary().negativeStock())
                .extracting(level -> level.product().getId()).contains(productTwo.getId());

        currentShop.set(TestShops.ONE);
        DashboardService.DashboardSummary mine = dashboardService.summary();
        assertThat(mine.negativeStock()).extracting(l -> l.product().getId()).doesNotContain(productTwo.getId());
        assertThat(mine.lowStock()).extracting(l -> l.product().getId()).doesNotContain(productTwo.getId());
    }
}
