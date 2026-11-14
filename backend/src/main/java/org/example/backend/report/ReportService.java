package org.example.backend.report;

import org.example.backend.expense.ExpenseService;
import org.example.backend.sale.SaleLineRepository;
import org.example.backend.sale.SaleQueryService;
import org.example.backend.shop.CurrentShop;
import org.example.backend.stock.StockLevelService;
import org.example.backend.stock.StockMovementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Owner-only profit report (finance-scope.md section 4). It adds no new definition of a
 * day, expected cash or an expense: sales totals come from SaleQueryService, expenses from
 * ExpenseService and stock value from StockLevelService. What is new is cost of goods, write-off
 * cost and the coverage counts, which are the only things that need the I2 cost snapshots.
 */
@Service
public class ReportService {

    /** A longer range is almost certainly a typo, and would scan the whole ledger. */
    static final int MAX_RANGE_DAYS = 366;
    /** The dashboard only shows a profit figure when at least this share of today's lines has a cost. */
    static final int DASHBOARD_MIN_COVERAGE_PERCENT = 80;
    private static final int TOP_PRODUCTS = 5;

    private final SaleQueryService saleQueryService;
    private final SaleLineRepository saleLineRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ExpenseService expenseService;
    private final StockLevelService stockLevelService;
    private final CurrentShop currentShop;

    public ReportService(SaleQueryService saleQueryService, SaleLineRepository saleLineRepository,
                         StockMovementRepository stockMovementRepository, ExpenseService expenseService,
                         StockLevelService stockLevelService, CurrentShop currentShop) {
        this.saleQueryService = saleQueryService;
        this.saleLineRepository = saleLineRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.expenseService = expenseService;
        this.stockLevelService = stockLevelService;
        this.currentShop = currentShop;
    }

    /** Month to date when no range is given; the range used is echoed back. */
    @Transactional(readOnly = true)
    public Report report(LocalDate fromDate, LocalDate toDate) {
        LocalDate end = toDate == null ? today() : toDate;
        LocalDate start = fromDate == null ? end.withDayOfMonth(1) : fromDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("The start date is after the end date");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Pick a range of at most " + MAX_RANGE_DAYS + " days");
        }
        Instant from = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        SaleQueryService.SaleTotals totals = saleQueryService.rangeTotals(start, end);
        BigDecimal netSales = totals.salesCash().add(totals.salesMomo());

        // Profit is worked out over costed lines only, so a line with no cost never counts as pure profit.
        Profit profit = profit(from, to);
        BigDecimal writeOffCost = money(stockMovementRepository.sumWriteOffCostBetween(currentShop.get(), from, to));
        long uncostedWriteOffs = stockMovementRepository.countUncostedWriteOffsBetween(currentShop.get(), from, to);

        ExpenseService.ExpenseList expenses = expenseService.list(start, end);
        BigDecimal netProfit = profit.grossProfit().subtract(writeOffCost).subtract(expenses.total());

        List<TopProduct> top = saleLineRepository
                .topProductsByProfit(currentShop.get(), from, to, PageRequest.of(0, TOP_PRODUCTS)).stream()
                .map(row -> new TopProduct((Long) row[0], (String) row[1], money((BigDecimal) row[2]),
                        money((BigDecimal) row[3]), money((BigDecimal) row[4])))
                .toList();

        StockLevelService.StockValue stockValue = stockLevelService.stockValue();
        return new Report(start, end, netSales, totals.discounts(), totals.salesCash(), totals.salesMomo(),
                totals.expectedCash(), totals.expectedMomo(), totals.expensesCash(), totals.expensesMomo(),
                profit.costedRevenue(), profit.costOfGoods(), profit.grossProfit(), profit.marginPercent(),
                writeOffCost, uncostedWriteOffs, expenses.total(), expenses.byCategory(), netProfit,
                profit.totalLines(), profit.costedLines(), profit.estimatedLines(), top,
                stockValue.value(), stockValue.productsWithoutCost());
    }

    /**
     * Today's gross profit for the dashboard, or null when too few of today's lines have a cost
     * to be worth showing (a half-costed catalogue would overstate profit).
     */
    @Transactional(readOnly = true)
    public TodayProfit todayProfit() {
        Instant from = saleQueryService.startOfToday();
        Instant to = from.plus(1, ChronoUnit.DAYS);
        Profit profit = profit(from, to);
        if (profit.totalLines() == 0
                || profit.costedLines() * 100 < profit.totalLines() * DASHBOARD_MIN_COVERAGE_PERCENT) {
            return null;
        }
        return new TodayProfit(profit.grossProfit(), profit.costedLines(), profit.totalLines());
    }

    private Profit profit(Instant from, Instant to) {
        BigDecimal costedRevenue = money(saleLineRepository.sumCostedRevenueBetween(currentShop.get(), from, to));
        BigDecimal costOfGoods = money(saleLineRepository.sumCostOfGoodsBetween(currentShop.get(), from, to));
        BigDecimal grossProfit = costedRevenue.subtract(costOfGoods);
        // Margin is on the same costed revenue, not on all net sales, otherwise a half-costed
        // catalogue would show a misleadingly low margin.
        BigDecimal margin = costedRevenue.signum() == 0 ? null
                : grossProfit.multiply(BigDecimal.valueOf(100)).divide(costedRevenue, 1, RoundingMode.HALF_UP);
        return new Profit(costedRevenue, costOfGoods, grossProfit, margin,
                saleLineRepository.countLinesBetween(currentShop.get(), from, to),
                saleLineRepository.countCostedLinesBetween(currentShop.get(), from, to),
                saleLineRepository.countEstimatedLinesBetween(currentShop.get(), from, to));
    }

    // quantity (2dp) x cost (2dp) has 4 decimals; round once, at the end of each sum.
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private record Profit(BigDecimal costedRevenue, BigDecimal costOfGoods, BigDecimal grossProfit,
                          BigDecimal marginPercent, long totalLines, long costedLines, long estimatedLines) {
    }

    public record TodayProfit(BigDecimal grossProfit, long costedLines, long totalLines) {
    }

    public record TopProduct(Long productId, String name, BigDecimal revenue, BigDecimal cost, BigDecimal profit) {
    }

    public record Report(LocalDate from, LocalDate to,
                         BigDecimal netSales, BigDecimal discounts, BigDecimal salesCash, BigDecimal salesMomo,
                         BigDecimal expectedCash, BigDecimal expectedMomo,
                         BigDecimal expensesCash, BigDecimal expensesMomo,
                         BigDecimal costedRevenue, BigDecimal costOfGoods, BigDecimal grossProfit,
                         BigDecimal marginPercent,
                         BigDecimal writeOffCost, long writeOffsWithoutCost,
                         BigDecimal expensesTotal, List<ExpenseService.CategoryTotal> expensesByCategory,
                         BigDecimal netProfit,
                         long totalLines, long costedLines, long estimatedLines,
                         List<TopProduct> topProducts,
                         BigDecimal stockValue, int productsWithoutCost) {
    }
}
