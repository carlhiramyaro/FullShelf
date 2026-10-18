package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.shop.TestShops;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The batched, shop-scoped queries behind the stock list screens must return
 * exactly what the per-product methods they replaced returned. Two fresh
 * shops (names prefixed "Batch Test") keep the dev database's rows in shop #1
 * out of the fixtures; one check also runs over shop #1 itself.
 */
@SpringBootTest
@Transactional
class StockBalanceBatchIntegrationTest {

    @Autowired
    private CurrentShop currentShop;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockMovementService stockMovementService;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private OpeningStockService openingStockService;
    @Autowired
    private ReverseStockService reverseStockService;

    private Shop shopA;
    private Shop shopB;
    private User ownerA;
    private User ownerB;
    private Product openingOnly;
    private Product mixed;
    private Product receivedThenReversed;
    private Product soldThenVoided;
    private Product noMovements;
    private Product shopBProduct;

    @BeforeEach
    void setUp() {
        shopA = shopRepository.saveAndFlush(new Shop("Batch Test Shop A"));
        shopB = shopRepository.saveAndFlush(new Shop("Batch Test Shop B"));
        ownerA = userRepository.save(new User(shopA, "Batch Owner A", UserRole.OWNER));
        ownerB = userRepository.save(new User(shopB, "Batch Owner B", UserRole.OWNER));

        openingOnly = product(shopA, "Batch Chicken");
        mixed = product(shopA, "Batch Oil");
        receivedThenReversed = product(shopA, "Batch Rice");
        soldThenVoided = product(shopA, "Batch Tomato");
        noMovements = product(shopA, "Batch Spice");
        // Same name as one of A's, to make the overlap real.
        shopBProduct = product(shopB, "Batch Chicken");

        record(openingOnly, "42.50", StockMovementType.OPENING, ownerA);

        record(mixed, "20", StockMovementType.OPENING, ownerA);
        record(mixed, "-8", StockMovementType.SALE, ownerA);
        record(mixed, "-2.50", StockMovementType.WRITE_OFF, ownerA);

        record(receivedThenReversed, "30", StockMovementType.OPENING, ownerA);
        StockMovement received = record(receivedThenReversed, "12", StockMovementType.RECEIVED, ownerA);
        stockMovementService.reverse(received, StockMovementType.REVERSAL, ownerA, null);
        record(receivedThenReversed, "5", StockMovementType.RECEIVED, ownerA);

        StockMovement sale = record(soldThenVoided, "-3", StockMovementType.SALE, ownerA);
        stockMovementService.reverse(sale, StockMovementType.VOID, ownerA, null);

        record(shopBProduct, "999", StockMovementType.OPENING, ownerB);
        record(shopBProduct, "-1", StockMovementType.SALE, ownerB);

        currentShop.set(shopA);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    @Test
    void batchedBalancesMatchThePerProductBalances() {
        Map<Long, BigDecimal> balances = stockBalanceService.currentBalances(shopA);

        for (Product product : List.of(openingOnly, mixed, receivedThenReversed, soldThenVoided, noMovements)) {
            assertThat(balances.getOrDefault(product.getId(), BigDecimal.ZERO))
                    .as(product.getName())
                    .isEqualByComparingTo(stockBalanceService.currentBalance(product.getId()));
        }
        assertThat(balances.get(openingOnly.getId())).isEqualByComparingTo("42.50");
        assertThat(balances.get(mixed.getId())).isEqualByComparingTo("9.50");
        assertThat(balances.get(receivedThenReversed.getId())).isEqualByComparingTo("35");
        assertThat(balances.get(soldThenVoided.getId())).isEqualByComparingTo("0");
    }

    @Test
    void aProductWithNoMovementsHasNoKeyAndReadsAsZero() {
        Map<Long, BigDecimal> balances = stockBalanceService.currentBalances(shopA);

        assertThat(balances).doesNotContainKey(noMovements.getId());
        assertThat(stockBalanceService.currentBalance(noMovements.getId())).isEqualByComparingTo("0");
    }

    @Test
    void batchedBalancesMatchPerProductBalancesForEveryProductInShopOne() {
        // Includes whatever the local dev database holds in shop #1.
        Map<Long, BigDecimal> balances = stockBalanceService.currentBalances(TestShops.ONE);

        for (Product product : productRepository.findByShopOrderByName(TestShops.ONE)) {
            assertThat(balances.getOrDefault(product.getId(), BigDecimal.ZERO))
                    .as(product.getName())
                    .isEqualByComparingTo(stockBalanceService.currentBalance(product.getId()));
        }
    }

    @Test
    void balancesAreScopedToTheShopAsked() {
        Map<Long, BigDecimal> forA = stockBalanceService.currentBalances(shopA);
        Map<Long, BigDecimal> forB = stockBalanceService.currentBalances(shopB);

        assertThat(forA).doesNotContainKey(shopBProduct.getId());
        assertThat(forB.keySet()).containsExactly(shopBProduct.getId());
        assertThat(forB.get(shopBProduct.getId())).isEqualByComparingTo("998");
    }

    @Test
    void unreversedCountsMatchTheOldPerProductLogic() {
        Map<Long, Long> reversible = stockBalanceService.unreversedCounts(
                shopA, List.of(StockMovementType.OPENING, StockMovementType.RECEIVED));

        // The reversed 12 kg RECEIVED row doesn't count; its opening and the later 5 kg do.
        assertThat(reversible.get(receivedThenReversed.getId())).isEqualTo(2L);
        assertThat(reversible.get(openingOnly.getId())).isEqualTo(1L);
        assertThat(reversible).doesNotContainKeys(soldThenVoided.getId(), noMovements.getId(), shopBProduct.getId());

        // Same number the drill-down page shows for each product.
        for (Product product : List.of(openingOnly, mixed, receivedThenReversed, soldThenVoided, noMovements)) {
            assertThat(reversible.getOrDefault(product.getId(), 0L).intValue())
                    .as(product.getName())
                    .isEqualTo(reverseStockService.reversibleMovements(product.getId()).size());
        }
    }

    @Test
    void reverseListReportsTheSameCountsAndBalances() {
        ReverseStockService.ProductReverseStatus status = reverseStockService.listStatus().stream()
                .filter(s -> s.product().getId().equals(receivedThenReversed.getId()))
                .findFirst().orElseThrow();

        assertThat(status.reversibleCount()).isEqualTo(2);
        assertThat(status.currentBalance()).isEqualByComparingTo("35");
    }

    @Test
    void openingListTreatsAReversedOpeningAsNotSetAndALiveOneAsSet() {
        StockMovement opening = stockMovementRepository
                .findByProductIdAndType(openingOnly.getId(), StockMovementType.OPENING).get(0);

        assertThat(openingStatusOf(openingOnly).alreadySet()).isTrue();
        assertThat(openingStatusOf(noMovements).alreadySet()).isFalse();

        stockMovementService.reverse(opening, StockMovementType.REVERSAL, ownerA, null);
        assertThat(openingStatusOf(openingOnly).alreadySet()).isFalse();

        // Re-entering the correct figure makes it set again, with a count of exactly one live row.
        record(openingOnly, "40", StockMovementType.OPENING, ownerA);
        assertThat(openingStatusOf(openingOnly).alreadySet()).isTrue();
        assertThat(stockBalanceService.unreversedCounts(shopA, List.of(StockMovementType.OPENING))
                .get(openingOnly.getId())).isEqualTo(1L);
    }

    @Test
    void openingBatchStillRejectsAProductWithALiveOpeningLineAndWritesNothing() {
        BigDecimal before = stockBalanceService.currentBalance(noMovements.getId());

        assertThatThrownBy(() -> openingStockService.recordOpeningBalances(
                Map.of(noMovements.getId(), new BigDecimal("10"), openingOnly.getId(), new BigDecimal("5")),
                ownerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been set");

        assertThat(stockBalanceService.currentBalance(noMovements.getId())).isEqualByComparingTo(before);
        assertThat(stockBalanceService.currentBalance(openingOnly.getId())).isEqualByComparingTo("42.50");
    }

    @Test
    void openingBatchStillAcceptsFreshProductsAndIgnoresAnotherShopsOpeningLines() {
        // Shop B's product has a live opening line; that must not block A's identically named product.
        openingStockService.recordOpeningBalances(Map.of(noMovements.getId(), new BigDecimal("10")), ownerA);

        assertThat(stockBalanceService.currentBalance(noMovements.getId())).isEqualByComparingTo("10");
    }

    private OpeningStockService.ProductOpeningStatus openingStatusOf(Product product) {
        return openingStockService.listStatus().stream()
                .filter(s -> s.product().getId().equals(product.getId()))
                .findFirst().orElseThrow();
    }

    private Product product(Shop shop, String name) {
        return productRepository.save(
                new Product(shop, name, ProductUnit.UNIT, new BigDecimal("10.00"), new BigDecimal("5.00")));
    }

    private StockMovement record(Product product, String quantity, StockMovementType type, User by) {
        return stockMovementService.record(product, new BigDecimal(quantity), type, by, null, null);
    }
}
