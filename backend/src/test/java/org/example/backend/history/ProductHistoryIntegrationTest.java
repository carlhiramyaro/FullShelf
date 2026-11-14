package org.example.backend.history;

import org.example.backend.product.Product;
import org.example.backend.product.ProductCostService;
import org.example.backend.product.ProductManagementService;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.report.ReportService;
import org.example.backend.sale.PaymentMethod;
import org.example.backend.sale.Sale;
import org.example.backend.sale.SaleService;
import org.example.backend.sale.VoidSaleService;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.stock.ReceiveStockService;
import org.example.backend.stock.ReverseStockService;
import org.example.backend.stock.StockMovement;
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
 * I5: product history. Fresh shops, so rows in the dev database's shop #1 can't move a figure.
 * The numbers are small so every balance and total can be checked by hand.
 */
@SpringBootTest
@Transactional
class ProductHistoryIntegrationTest {

    @Autowired private CurrentShop currentShop;
    @Autowired private ShopRepository shopRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductManagementService productManagementService;
    @Autowired private ProductCostService productCostService;
    @Autowired private ReceiveStockService receiveStockService;
    @Autowired private WriteOffService writeOffService;
    @Autowired private ReverseStockService reverseStockService;
    @Autowired private StockMovementService stockMovementService;
    @Autowired private SaleService saleService;
    @Autowired private VoidSaleService voidSaleService;
    @Autowired private ReportService reportService;
    @Autowired private ProductHistoryService historyService;

    private Shop shop;
    private User owner;
    private User staff;

    @BeforeEach
    void setUp() {
        shop = shopRepository.saveAndFlush(new Shop("History Shop A"));
        owner = userRepository.save(new User(shop, "History Owner A", UserRole.OWNER));
        staff = new User(shop, "History Staff A", UserRole.STAFF);
        staff.setPinHash("x");
        staff = userRepository.save(staff);
        currentShop.set(shop);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    private Product newProduct(String name, String price, String cost) {
        return productManagementService.createProduct(name, ProductUnit.UNIT, new BigDecimal(price),
                new BigDecimal("1.00"), null, cost == null ? null : new BigDecimal(cost), owner);
    }

    private Sale sell(Product product, String quantity, String discount) {
        return saleService.confirmSale(
                List.of(new SaleService.LineRequest(product.getId(), new BigDecimal(quantity), new BigDecimal(discount))),
                staff.getId(), PaymentMethod.CASH).sale();
    }

    private ProductHistoryService.History all(Product product) {
        return historyService.history(product.getId(), null, null, null, 0);
    }

    @Test
    void runningBalanceIsAfterEachRowAndSurvivesFilters() {
        Product p = newProduct("History Oil", "10.00", null);
        stockMovementService.record(p, new BigDecimal("100"), StockMovementType.OPENING, owner, null, null);
        receiveStockService.receive(p.getId(), null, new BigDecimal("50"), owner);
        sell(p, "30", "0");
        writeOffService.writeOff(p.getId(), new BigDecimal("5"), "spoiled", owner);

        List<ProductHistoryService.Row> ledger = all(p).rows().stream().filter(r -> r.kind().equals("LEDGER")).toList();
        // Newest first: write-off, sale, received, opening.
        assertThat(ledger).extracting(ProductHistoryService.Row::type)
                .containsExactly("WRITE_OFF", "SALE", "RECEIVED", "OPENING");
        assertThat(ledger).extracting(ProductHistoryService.Row::balanceAfter)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("115"), new BigDecimal("120"), new BigDecimal("150"), new BigDecimal("100"));

        // Filtering to sales must not change the balance after the sale (it depends on the rows we hid).
        ProductHistoryService.History sales = historyService.history(p.getId(), null, null, "SALE", 0);
        assertThat(sales.rows()).hasSize(1);
        assertThat(sales.rows().get(0).balanceAfter()).isEqualByComparingTo("120");
        assertThat(all(p).product().currentBalance()).isEqualByComparingTo("115");
    }

    @Test
    void salesLinkToTheirReceiptAndVoidsAndReversalsLinkBack() {
        Product p = newProduct("History Rice", "10.00", null);
        StockMovement opening = stockMovementService.record(p, new BigDecimal("100"), StockMovementType.OPENING,
                owner, null, null);
        Sale sale = sell(p, "10", "0");
        voidSaleService.voidSale(sale.getReceiptNumber(), null, owner);
        reverseStockService.reverseEntry(opening.getId(), "typo", owner);

        List<ProductHistoryService.Row> ledger = all(p).rows().stream().filter(r -> r.kind().equals("LEDGER")).toList();
        // Newest first: reversal, void, sale, opening.
        assertThat(ledger).extracting(ProductHistoryService.Row::type)
                .containsExactly("REVERSAL", "VOID", "SALE", "OPENING");
        assertThat(ledger.get(0).reversedMovementId()).isEqualTo(opening.getId());
        assertThat(ledger.get(1).receiptNumber()).isEqualTo(sale.getReceiptNumber()); // the void points at the receipt it undid
        assertThat(ledger.get(2).receiptNumber()).isEqualTo(sale.getReceiptNumber());
        assertThat(ledger.get(3).receiptNumber()).isNull();
        assertThat(ledger.get(0).staffName()).isEqualTo("History Owner A");
    }

    @Test
    void priceAndCostChangesAreRecordedOnlyWhenSomethingChanged() {
        Product p = newProduct("History Beans", "10.00", "6.00");
        Long id = p.getId();
        // An update that changes only the name leaves no price row.
        productManagementService.updateProduct(id, "History Beans 2", ProductUnit.UNIT, new BigDecimal("10.00"),
                new BigDecimal("1.00"), null, null, owner);
        // Price change, then a cost change typed on the product form, then a cost change on Receive stock.
        productManagementService.updateProduct(id, "History Beans 2", ProductUnit.UNIT, new BigDecimal("12.50"),
                new BigDecimal("1.00"), null, null, owner);
        productManagementService.updateProduct(id, "History Beans 2", ProductUnit.UNIT, new BigDecimal("12.50"),
                new BigDecimal("1.00"), null, new BigDecimal("7.00"), owner);
        receiveStockService.receive(id, null, new BigDecimal("10"), new BigDecimal("8.00"), owner);
        // Receiving at the same cost is not a change.
        receiveStockService.receive(id, null, new BigDecimal("10"), new BigDecimal("8.00"), owner);

        ProductHistoryService.History h = historyService.history(id, null, null, "PRICE_CHANGE", 0);
        assertThat(h.rows()).hasSize(4); // created, price, cost, delivery cost
        ProductHistoryService.Row newest = h.rows().get(0);
        assertThat(newest.kind()).isEqualTo("PRICE_CHANGE");
        assertThat(newest.costPrice()).isEqualByComparingTo("8.00");
        assertThat(newest.previousCost()).isEqualByComparingTo("7.00");
        assertThat(newest.price()).isEqualByComparingTo("12.50");
        assertThat(newest.staffName()).isEqualTo("History Owner A");
        ProductHistoryService.Row first = h.rows().get(3);
        assertThat(first.previousPrice()).isNull(); // the starting point has no "before"
        assertThat(first.price()).isEqualByComparingTo("10.00");
        // Price changes sit in the same timeline as ledger rows when no filter is given.
        assertThat(all(p).rows()).extracting(ProductHistoryService.Row::kind).contains("PRICE_CHANGE", "LEDGER");
    }

    @Test
    void summaryCoversTheRangeAndMatchesTheReport() {
        Product p = newProduct("History Salt", "10.00", "6.00");
        stockMovementService.record(p, new BigDecimal("100"), StockMovementType.OPENING, owner, null, null);
        receiveStockService.receive(p.getId(), null, new BigDecimal("20"), owner);
        sell(p, "10", "0");     // revenue 100, cost 60
        sell(p, "5", "5.00");   // revenue 45, cost 30
        Sale wrong = sell(p, "3", "0");
        voidSaleService.voidSale(wrong.getReceiptNumber(), null, owner);
        writeOffService.writeOff(p.getId(), new BigDecimal("2"), "damaged", owner);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ProductHistoryService.Summary s = historyService.history(p.getId(), today, today, null, 0).summary();
        assertThat(s.received()).isEqualByComparingTo("20");
        assertThat(s.sold()).isEqualByComparingTo("15");       // the voided sale does not count
        assertThat(s.writtenOff()).isEqualByComparingTo("2");
        // +100 +20 -10 -5 -3 +3 -2
        assertThat(s.netChange()).isEqualByComparingTo("103");
        assertThat(s.revenue()).isEqualByComparingTo("145.00");
        assertThat(s.discounts()).isEqualByComparingTo("5.00");
        assertThat(s.averagePrice()).isEqualByComparingTo("9.67");
        assertThat(s.sales()).isEqualTo(2);
        assertThat(s.grossProfit()).isEqualByComparingTo("55.00");
        assertThat(s.marginPercent()).isEqualByComparingTo("37.9");

        // Same product, same day, same answer as the Reports screen (this is the shop's only product with sales).
        ReportService.Report report = reportService.report(today, today);
        assertThat(report.grossProfit()).isEqualByComparingTo(s.grossProfit());

        // A range that excludes today is empty.
        LocalDate lastWeek = today.minusDays(7);
        ProductHistoryService.History old = historyService.history(p.getId(), lastWeek, lastWeek, null, 0);
        assertThat(old.rows()).isEmpty();
        assertThat(old.summary().sold()).isEqualByComparingTo("0");
        assertThat(old.summary().averagePrice()).isNull();
        assertThat(old.summary().grossProfit()).isNull();
    }

    @Test
    void profitIsOmittedWhenNoLineHasACost() {
        Product p = newProduct("History Uncosted", "10.00", null);
        stockMovementService.record(p, new BigDecimal("10"), StockMovementType.OPENING, owner, null, null);
        sell(p, "2", "0");
        ProductHistoryService.Summary s = all(p).summary();
        assertThat(s.revenue()).isEqualByComparingTo("20.00");
        assertThat(s.grossProfit()).isNull();
        assertThat(s.marginPercent()).isNull();
        assertThat(s.saleLines()).isEqualTo(1);
        assertThat(s.costedLines()).isZero();
    }

    @Test
    void pagesAreFiftyRowsNewestFirstWithNoGapsOrRepeats() {
        Product p = newProduct("History Busy", "10.00", null);
        for (int i = 1; i <= 60; i++) {
            stockMovementService.record(p, BigDecimal.ONE, StockMovementType.RECEIVED, owner, null, "n" + i);
        }
        // 60 ledger rows + 1 price row (the product's starting point) = 61.
        ProductHistoryService.History first = historyService.history(p.getId(), null, null, null, 0);
        ProductHistoryService.History second = historyService.history(p.getId(), null, null, null, 1);
        assertThat(first.total()).isEqualTo(61);
        assertThat(first.rows()).hasSize(50);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.rows()).hasSize(11);
        assertThat(second.hasNext()).isFalse();
        assertThat(first.rows().get(0).note()).isEqualTo("n60");
        assertThat(first.rows().get(0).balanceAfter()).isEqualByComparingTo("60");
        // The balance on page two continues from page one: the 11th-oldest row is balance 10.
        List<ProductHistoryService.Row> ledgerOnPage2 = second.rows().stream()
                .filter(r -> r.kind().equals("LEDGER")).toList();
        assertThat(ledgerOnPage2.get(0).note()).isEqualTo("n10");
        assertThat(ledgerOnPage2.get(0).balanceAfter()).isEqualByComparingTo("10");
    }

    @Test
    void anotherShopsProductIsNotFoundAndItsRowsNeverAppear() {
        Product mine = newProduct("History Same Name", "10.00", null);
        stockMovementService.record(mine, new BigDecimal("5"), StockMovementType.OPENING, owner, null, null);

        Shop other = shopRepository.saveAndFlush(new Shop("History Shop B"));
        User otherOwner = userRepository.save(new User(other, "History Owner B", UserRole.OWNER));
        currentShop.set(other);
        Product theirs = productManagementService.createProduct("History Same Name", ProductUnit.UNIT,
                new BigDecimal("99.00"), BigDecimal.ONE, null, null, otherOwner);
        stockMovementService.record(theirs, new BigDecimal("77"), StockMovementType.OPENING, otherOwner, null, null);

        // Back in shop A: B's id is "No product", and A's own history holds none of B's rows.
        currentShop.set(shop);
        assertThatThrownBy(() -> historyService.history(theirs.getId(), null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No product " + theirs.getId());
        ProductHistoryService.History own = all(mine);
        assertThat(own.rows()).extracting(ProductHistoryService.Row::staffName).doesNotContain("History Owner B");
        assertThat(own.product().currentBalance()).isEqualByComparingTo("5");
    }

    @Test
    void badInputIsRejected() {
        Product p = newProduct("History Bad", "10.00", null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThatThrownBy(() -> historyService.history(p.getId(), today, today.minusDays(1), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> historyService.history(p.getId(), null, null, "NOPE", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> historyService.history(p.getId(), null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
