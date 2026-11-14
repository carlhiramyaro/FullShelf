package org.example.backend.product;

import org.example.backend.sale.SaleService;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.shop.TestShops;
import org.example.backend.stock.ReceiveStockService;
import org.example.backend.stock.StockLevelService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I2: cost price, the cost snapshot on sale lines and write-offs, the one-time fill,
 * and stock value. Rows are read back with JDBC because the fill is a bulk UPDATE
 * that leaves entities already in the persistence context stale.
 */
@SpringBootTest
@Transactional
class CostPriceIntegrationTest {

    @Autowired private CurrentShop currentShop;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ProductManagementService productManagementService;
    @Autowired private ProductCostService productCostService;
    @Autowired private SaleService saleService;
    @Autowired private WriteOffService writeOffService;
    @Autowired private ReceiveStockService receiveStockService;
    @Autowired private StockMovementService stockMovementService;
    @Autowired private StockLevelService stockLevelService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private User staff;

    @BeforeEach
    void setUp() {
        currentShop.set(TestShops.ONE);
        owner = userRepository.save(new User(TestShops.ONE, "Cost Test Owner", UserRole.OWNER));
        staff = userRepository.save(new User(TestShops.ONE, "Cost Test Staff", UserRole.STAFF));
    }

    @AfterEach
    void clearCurrentShop() {
        currentShop.clear();
    }

    private Product product(String name, BigDecimal opening) {
        Product product = productRepository.save(new Product(TestShops.ONE,
                name + " " + System.nanoTime(), ProductUnit.KG, new BigDecimal("40.00"), new BigDecimal("5.00")));
        if (opening != null) {
            stockMovementService.record(product, opening, StockMovementType.OPENING, owner, null, null);
        }
        return product;
    }

    private void sell(Product product, String quantity) {
        saleService.confirmSale(List.of(new SaleService.LineRequest(product.getId(), new BigDecimal(quantity), null)),
                staff.getId());
    }

    private List<Map<String, Object>> lines(Product product) {
        return jdbcTemplate.queryForList(
                "select unit_cost, cost_estimated from sale_lines where product_id = ? order by id", product.getId());
    }

    private List<Map<String, Object>> writeOffs(Product product) {
        return jdbcTemplate.queryForList(
                "select unit_cost from stock_movements where product_id = ? and type = 'WRITE_OFF' order by id",
                product.getId());
    }

    private void setCost(Product product, String cost) {
        productManagementService.updateProduct(product.getId(), product.getName(), product.getUnit(),
                product.getPrice(), product.getAlertLevel(), product.getCartonWeight(), new BigDecimal(cost));
    }

    @Test
    void firstCostFillsOldLinesAndWriteOffsAsEstimatedAndOnlyOnce() {
        Product chicken = product("Cost Test Chicken", new BigDecimal("100.00"));
        sell(chicken, "2.00");
        sell(chicken, "1.00");
        writeOffService.writeOff(chicken.getId(), new BigDecimal("1.50"), "spoiled", owner);

        assertThat(lines(chicken)).allSatisfy(l -> {
            assertThat(l.get("unit_cost")).isNull();
            assertThat(l.get("cost_estimated")).isEqualTo(false);
        });

        setCost(chicken, "30.00");

        assertThat(lines(chicken)).hasSize(2).allSatisfy(l -> {
            assertThat((BigDecimal) l.get("unit_cost")).isEqualByComparingTo("30.00");
            assertThat(l.get("cost_estimated")).isEqualTo(true);
        });
        assertThat(writeOffs(chicken)).singleElement()
                .satisfies(w -> assertThat((BigDecimal) w.get("unit_cost")).isEqualByComparingTo("30.00"));

        // A later change must not rewrite anything already filled.
        setCost(chicken, "35.00");
        assertThat(lines(chicken)).allSatisfy(l ->
                assertThat((BigDecimal) l.get("unit_cost")).isEqualByComparingTo("30.00"));
        assertThat(writeOffs(chicken)).singleElement()
                .satisfies(w -> assertThat((BigDecimal) w.get("unit_cost")).isEqualByComparingTo("30.00"));
    }

    @Test
    void aSaleSnapshotsTheCurrentCostAndLaterChangesDoNotMoveIt() {
        Product chicken = product("Cost Test Snapshot", new BigDecimal("100.00"));
        setCost(chicken, "30.00");
        sell(chicken, "1.00");
        setCost(chicken, "36.00");
        sell(chicken, "1.00");

        List<Map<String, Object>> lines = lines(chicken);
        assertThat((BigDecimal) lines.get(0).get("unit_cost")).isEqualByComparingTo("30.00");
        assertThat((BigDecimal) lines.get(1).get("unit_cost")).isEqualByComparingTo("36.00");
        assertThat(lines).allSatisfy(l -> assertThat(l.get("cost_estimated")).isEqualTo(false));
    }

    @Test
    void aSaleOfAProductWithNoCostKeepsTheLineCostNull() {
        Product chicken = product("Cost Test No Cost", new BigDecimal("100.00"));
        sell(chicken, "1.00");
        assertThat(lines(chicken)).singleElement().satisfies(l -> assertThat(l.get("unit_cost")).isNull());
    }

    @Test
    void aWriteOffStampsTheCostAtThatMoment() {
        Product chicken = product("Cost Test Write-off", new BigDecimal("100.00"));
        setCost(chicken, "30.00");
        writeOffService.writeOff(chicken.getId(), new BigDecimal("1.00"), "spoiled", owner);
        setCost(chicken, "40.00");
        writeOffService.writeOff(chicken.getId(), new BigDecimal("1.00"), "spoiled again", owner);

        List<Map<String, Object>> rows = writeOffs(chicken);
        assertThat((BigDecimal) rows.get(0).get("unit_cost")).isEqualByComparingTo("30.00");
        assertThat((BigDecimal) rows.get(1).get("unit_cost")).isEqualByComparingTo("40.00");
    }

    @Test
    void aCostCanBeChangedButNotClearedAndNullMeansUnchanged() {
        Product chicken = product("Cost Test Clear", null);
        setCost(chicken, "30.00");

        productManagementService.updateProduct(chicken.getId(), chicken.getName(), chicken.getUnit(),
                chicken.getPrice(), chicken.getAlertLevel(), chicken.getCartonWeight(), null);

        assertThat(productRepository.findByIdAndShop(chicken.getId(), TestShops.ONE).orElseThrow().getCostPrice())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void zeroIsAValidCostAndFillsLikeAnyOther() {
        Product chicken = product("Cost Test Zero", new BigDecimal("10.00"));
        sell(chicken, "1.00");
        setCost(chicken, "0.00");

        assertThat(lines(chicken)).singleElement().satisfies(l -> {
            assertThat((BigDecimal) l.get("unit_cost")).isEqualByComparingTo("0.00");
            assertThat(l.get("cost_estimated")).isEqualTo(true);
        });
    }

    @Test
    void rejectsANegativeCostAndMoreThanTwoDecimals() {
        Product chicken = product("Cost Test Invalid", null);
        assertThatThrownBy(() -> setCost(chicken, "-1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> setCost(chicken, "1.005")).isInstanceOf(IllegalArgumentException.class);
        assertThat(productRepository.findByIdAndShop(chicken.getId(), TestShops.ONE).orElseThrow().getCostPrice())
                .isNull();
    }

    @Test
    void receivingWithACostSetsItAndRunsTheFirstCostFill() {
        Product chicken = product("Cost Test Receive", new BigDecimal("50.00"));
        sell(chicken, "2.00");

        receiveStockService.receive(chicken.getId(), null, new BigDecimal("20.00"), new BigDecimal("28.50"), owner);

        assertThat(productRepository.findByIdAndShop(chicken.getId(), TestShops.ONE).orElseThrow().getCostPrice())
                .isEqualByComparingTo("28.50");
        assertThat(lines(chicken)).singleElement().satisfies(l -> {
            assertThat((BigDecimal) l.get("unit_cost")).isEqualByComparingTo("28.50");
            assertThat(l.get("cost_estimated")).isEqualTo(true);
        });

        // Without a cost the product's cost is left alone.
        receiveStockService.receive(chicken.getId(), null, new BigDecimal("5.00"), owner);
        assertThat(productRepository.findByIdAndShop(chicken.getId(), TestShops.ONE).orElseThrow().getCostPrice())
                .isEqualByComparingTo("28.50");
    }

    @Test
    void stockValueCountsNegativeAsZeroAndReportsProductsWithNoCost() {
        StockLevelService.StockValue before = stockLevelService.stockValue();

        Product valued = product("Cost Test Valued", new BigDecimal("10.00"));
        setCost(valued, "3.50");                                     // 10 x 3.50 = 35.00
        Product negative = product("Cost Test Negative", new BigDecimal("1.00"));
        setCost(negative, "100.00");
        sell(negative, "4.00");                                      // balance -3, must add nothing
        product("Cost Test Uncosted", new BigDecimal("50.00"));      // left out, counted

        StockLevelService.StockValue after = stockLevelService.stockValue();
        assertThat(after.value().subtract(before.value())).isEqualByComparingTo("35.00");
        assertThat(after.productsWithoutCost() - before.productsWithoutCost()).isEqualTo(1);
    }

    @Test
    void anotherShopsCostsNeverTouchThisShopsLinesOrValue() {
        Product mine = product("Cost Test Mine", new BigDecimal("10.00"));
        sell(mine, "1.00");
        StockLevelService.StockValue before = stockLevelService.stockValue();

        Shop two = shopRepository.saveAndFlush(new Shop("Cost Test Shop Two"));
        Product theirs = productRepository.save(new Product(two, "Cost Test Mine", ProductUnit.KG,
                new BigDecimal("40.00"), new BigDecimal("5.00")));
        User twoOwner = userRepository.save(new User(two, "Two Owner", UserRole.OWNER));
        stockMovementService.record(theirs, new BigDecimal("99.00"), StockMovementType.OPENING, twoOwner, null, null);
        currentShop.set(two);
        productCostService.applyCost(theirs, new BigDecimal("50.00"));
        currentShop.set(TestShops.ONE);

        assertThat(lines(mine)).singleElement().satisfies(l -> assertThat(l.get("unit_cost")).isNull());
        StockLevelService.StockValue after = stockLevelService.stockValue();
        assertThat(after.value()).isEqualByComparingTo(before.value());
        assertThat(after.productsWithoutCost()).isEqualTo(before.productsWithoutCost());
    }
}
