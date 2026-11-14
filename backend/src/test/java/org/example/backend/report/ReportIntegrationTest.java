package org.example.backend.report;

import org.example.backend.dashboard.DashboardService;
import org.example.backend.expense.ExpenseService;
import org.example.backend.product.Product;
import org.example.backend.product.ProductCostService;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.sale.PaymentMethod;
import org.example.backend.sale.Sale;
import org.example.backend.sale.SaleService;
import org.example.backend.sale.VoidSaleService;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.stock.StockMovementService;
import org.example.backend.stock.StockMovementType;
import org.example.backend.stock.WriteOffService;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I4: the profit report. Fresh shops, so the dev database's rows in shop #1 can't move a figure.
 * Prices and costs are chosen so every expected number can be checked by hand.
 */
@SpringBootTest
@Transactional
class ReportIntegrationTest {

    @Autowired private CurrentShop currentShop;
    @Autowired private ShopRepository shopRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCostService productCostService;
    @Autowired private SaleService saleService;
    @Autowired private VoidSaleService voidSaleService;
    @Autowired private WriteOffService writeOffService;
    @Autowired private StockMovementService stockMovementService;
    @Autowired private ExpenseService expenseService;
    @Autowired private ReportService reportService;
    @Autowired private DashboardService dashboardService;

    private Shop shop;
    private User owner;
    private User staff;

    @BeforeEach
    void setUp() {
        shop = shopRepository.saveAndFlush(new Shop("Report Shop A"));
        owner = userRepository.save(new User(shop, "Report Owner A", UserRole.OWNER));
        staff = new User(shop, "Report Staff A", UserRole.STAFF);
        staff.setPinHash("x");
        staff = userRepository.save(staff);
        currentShop.set(shop);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    private Product product(String name, String price, String cost) {
        Product product = productRepository.save(
                new Product(shop, name, ProductUnit.UNIT, new BigDecimal(price), new BigDecimal("1.00")));
        stockMovementService.record(product, new BigDecimal("1000"), StockMovementType.OPENING, owner, null, null);
        if (cost != null) {
            productCostService.applyCost(product, new BigDecimal(cost));
        }
        return product;
    }

    private Sale sell(Product product, String quantity, String discount, PaymentMethod method) {
        return saleService.confirmSale(
                List.of(new SaleService.LineRequest(product.getId(), new BigDecimal(quantity), new BigDecimal(discount))),
                staff.getId(), method).sale();
    }

    private ReportService.Report today() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return reportService.report(today, today);
    }

    @Test
    void profitIsWorkedOutOverCostedLinesOnlyAndMarginUsesTheSameLines() {
        Product costed = product("Report Oil", "10.00", "6.00");
        Product uncosted = product("Report Salt", "5.00", null);
        sell(costed, "10", "0", PaymentMethod.CASH);   // revenue 100, cost 60, profit 40
        sell(costed, "5", "5.00", PaymentMethod.MOMO); // revenue 45,  cost 30, profit 15
        sell(uncosted, "4", "0", PaymentMethod.CASH);  // revenue 20, no cost: not in profit

        ReportService.Report report = today();
        assertThat(report.netSales()).isEqualByComparingTo("165.00");
        assertThat(report.discounts()).isEqualByComparingTo("5.00");
        assertThat(report.salesCash()).isEqualByComparingTo("120.00");
        assertThat(report.salesMomo()).isEqualByComparingTo("45.00");
        assertThat(report.costedRevenue()).isEqualByComparingTo("145.00");
        assertThat(report.costOfGoods()).isEqualByComparingTo("90.00");
        assertThat(report.grossProfit()).isEqualByComparingTo("55.00");
        // 55 / 145, not 55 / 165.
        assertThat(report.marginPercent()).isEqualByComparingTo("37.9");
        assertThat(report.totalLines()).isEqualTo(3);
        assertThat(report.costedLines()).isEqualTo(2);
        assertThat(report.estimatedLines()).isZero();
    }

    @Test
    void netProfitSubtractsWriteOffCostAndEveryNonVoidedExpenseWhateverItWasPaidFrom() {
        Product oil = product("Report Oil 2", "10.00", "6.00");
        sell(oil, "10", "0", PaymentMethod.CASH); // gross profit 40
        writeOffService.writeOff(oil.getId(), new BigDecimal("2"), "spoiled", owner); // 2 x 6 = 12
        LocalDate today = ExpenseService.today();
        expenseService.add(today, new BigDecimal("5.00"), "RENT", "CASH", null, owner);
        expenseService.add(today, new BigDecimal("3.00"), "TRANSPORT", "OTHER", null, owner);
        ExpenseService.ExpenseView voided = expenseService.add(today, new BigDecimal("100.00"), "SUPPLIES", "MOMO", null, owner);
        expenseService.voidExpense(voided.id(), null, owner);

        ReportService.Report report = today();
        assertThat(report.writeOffCost()).isEqualByComparingTo("12.00");
        assertThat(report.expensesTotal()).isEqualByComparingTo("8.00");
        assertThat(report.expensesByCategory()).hasSize(2);
        assertThat(report.netProfit()).isEqualByComparingTo("20.00"); // 40 - 12 - 8
        // The cash figures still follow I3: cash sales minus cash expenses.
        assertThat(report.expectedCash()).isEqualByComparingTo("95.00");
    }

    @Test
    void aWriteOffWithNoCostIsCountedNotValued() {
        Product salt = product("Report Salt 2", "5.00", null);
        writeOffService.writeOff(salt.getId(), new BigDecimal("3"), "damaged", owner);

        ReportService.Report report = today();
        assertThat(report.writeOffCost()).isEqualByComparingTo("0");
        assertThat(report.writeOffsWithoutCost()).isEqualTo(1);
    }

    @Test
    void aVoidedSaleCountsInNothing() {
        Product oil = product("Report Oil 3", "10.00", "6.00");
        Sale wrong = sell(oil, "10", "0", PaymentMethod.CASH);
        sell(oil, "1", "0", PaymentMethod.CASH);
        voidSaleService.voidSale(wrong.getReceiptNumber(), null, owner);

        ReportService.Report report = today();
        assertThat(report.netSales()).isEqualByComparingTo("10.00");
        assertThat(report.grossProfit()).isEqualByComparingTo("4.00");
        assertThat(report.totalLines()).isEqualTo(1);
    }

    @Test
    void linesFilledByTheOneTimeFillAreCountedAsEstimated() {
        Product oil = product("Report Oil 4", "10.00", null);
        sell(oil, "10", "0", PaymentMethod.CASH);
        productCostService.applyCost(oil, new BigDecimal("6.00")); // fills the old line, estimated
        sell(oil, "1", "0", PaymentMethod.CASH);                   // a real snapshot

        ReportService.Report report = today();
        assertThat(report.totalLines()).isEqualTo(2);
        assertThat(report.costedLines()).isEqualTo(2);
        assertThat(report.estimatedLines()).isEqualTo(1);
        assertThat(report.grossProfit()).isEqualByComparingTo("44.00");
    }

    @Test
    void topProductsAreRankedByProfitNotBySalesAndLimitedToFive() {
        Product bigSeller = product("Report Rice", "10.00", "9.00"); // sells 100, profit 10
        Product earner = product("Report Spice", "10.00", "2.00");   // sells 30,  profit 24
        sell(bigSeller, "10", "0", PaymentMethod.CASH);
        sell(earner, "3", "0", PaymentMethod.CASH);
        for (int i = 0; i < 5; i++) {
            sell(product("Report Filler " + i, "2.00", "1.00"), "1", "0", PaymentMethod.CASH);
        }

        List<ReportService.TopProduct> top = today().topProducts();
        assertThat(top).hasSize(5);
        assertThat(top.get(0).name()).isEqualTo("Report Spice");
        assertThat(top.get(0).profit()).isEqualByComparingTo("24.00");
        assertThat(top.get(1).name()).isEqualTo("Report Rice");
    }

    @Test
    void theRangeIsInclusiveAndSalesOutsideItAreLeftOut() {
        Product oil = product("Report Oil 5", "10.00", "6.00");
        sell(oil, "1", "0", PaymentMethod.CASH);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(reportService.report(today.minusDays(3), today.minusDays(1)).netSales()).isEqualByComparingTo("0");
        assertThat(reportService.report(today.minusDays(3), today).netSales()).isEqualByComparingTo("10.00");
    }

    @Test
    void noRangeMeansMonthToDateAndEchoesIt() {
        ReportService.Report report = reportService.report(null, null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(report.from()).isEqualTo(today.withDayOfMonth(1));
        assertThat(report.to()).isEqualTo(today);
    }

    @Test
    void aReversedOrTooLongRangeIsRejected() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThatThrownBy(() -> reportService.report(today, today.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reportService.report(today.minusDays(366), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("366");
        // Exactly 366 days is allowed.
        assertThat(reportService.report(today.minusDays(365), today)).isNotNull();
    }

    @Test
    void anotherShopsSalesCostsWriteOffsAndExpensesNeverMoveThisShopsReport() {
        Shop other = shopRepository.saveAndFlush(new Shop("Report Shop B"));
        User otherOwner = userRepository.save(new User(other, "Report Owner B", UserRole.OWNER));
        User otherStaff = new User(other, "Report Staff B", UserRole.STAFF);
        otherStaff.setPinHash("x");
        otherStaff = userRepository.save(otherStaff);
        currentShop.set(other);
        Product theirs = productRepository.save(
                new Product(other, "Report Oil", ProductUnit.UNIT, new BigDecimal("10.00"), new BigDecimal("1.00")));
        stockMovementService.record(theirs, new BigDecimal("100"), StockMovementType.OPENING, otherOwner, null, null);
        productCostService.applyCost(theirs, new BigDecimal("1.00"));
        saleService.confirmSale(List.of(new SaleService.LineRequest(theirs.getId(), new BigDecimal("10"), BigDecimal.ZERO)),
                otherStaff.getId());
        writeOffService.writeOff(theirs.getId(), new BigDecimal("1"), "lost", otherOwner);
        expenseService.add(ExpenseService.today(), new BigDecimal("50.00"), "RENT", "CASH", null, otherOwner);
        assertThat(today().grossProfit()).isEqualByComparingTo("90.00"); // sanity: B really has data

        currentShop.set(shop);
        ReportService.Report report = today();
        assertThat(report.netSales()).isEqualByComparingTo("0");
        assertThat(report.grossProfit()).isEqualByComparingTo("0");
        assertThat(report.writeOffCost()).isEqualByComparingTo("0");
        assertThat(report.expensesTotal()).isEqualByComparingTo("0");
        assertThat(report.netProfit()).isEqualByComparingTo("0");
        assertThat(report.totalLines()).isZero();
        assertThat(report.topProducts()).isEmpty();
    }

    @Test
    void theDashboardShowsTodaysProfitOnlyWhenAtLeast80PercentOfLinesHaveACost() {
        Product costed = product("Report Oil 6", "10.00", "6.00");
        Product uncosted = product("Report Salt 3", "5.00", null);
        assertThat(dashboardService.summary().todayProfit()).isNull(); // no sales at all

        for (int i = 0; i < 3; i++) {
            sell(costed, "1", "0", PaymentMethod.CASH);
        }
        sell(uncosted, "1", "0", PaymentMethod.CASH); // 3 of 4 = 75%
        assertThat(dashboardService.summary().todayProfit()).isNull();

        sell(costed, "1", "0", PaymentMethod.CASH);   // 4 of 5 = 80%
        ReportService.TodayProfit profit = dashboardService.summary().todayProfit();
        assertThat(profit).isNotNull();
        assertThat(profit.grossProfit()).isEqualByComparingTo("16.00");
        assertThat(profit.costedLines()).isEqualTo(4);
        assertThat(profit.totalLines()).isEqualTo(5);
    }
}
