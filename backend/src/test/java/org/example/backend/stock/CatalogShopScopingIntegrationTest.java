package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.product.ProductManagementService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G3 smoke check: the caller is in shop 1, the product and movement belong to
 * another shop. Every catalog and stock service must treat them as not
 * existing. The full cross-shop suite is G6.
 */
@SpringBootTest
@Transactional
class CatalogShopScopingIntegrationTest {

    @Autowired
    private CurrentShop currentShop;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private ProductManagementService productManagementService;
    @Autowired
    private OpeningStockService openingStockService;
    @Autowired
    private ReceiveStockService receiveStockService;
    @Autowired
    private WriteOffService writeOffService;
    @Autowired
    private ReverseStockService reverseStockService;
    @Autowired
    private StockLevelService stockLevelService;
    @Autowired
    private StockMovementService stockMovementService;

    private User ownerTwo;
    private Product productTwo;
    private StockMovement movementTwo;

    @BeforeEach
    void setUp() {
        Shop two = shopRepository.saveAndFlush(new Shop("Scoping Shop Two"));
        ownerTwo = userRepository.save(new User(two, "Scoping Owner Two", UserRole.OWNER));
        productTwo = productRepository.save(
                new Product(two, "Scoping Oil", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));
        movementTwo = stockMovementService.record(
                productTwo, new BigDecimal("10"), StockMovementType.RECEIVED, ownerTwo, null, null);
        currentShop.set(TestShops.ONE);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    @Test
    void listsOnlyTheCallersShop() {
        assertThat(productManagementService.listProducts()).extracting(Product::getId)
                .doesNotContain(productTwo.getId());
        assertThat(stockLevelService.listLevels()).extracting(l -> l.product().getId())
                .doesNotContain(productTwo.getId());
        assertThat(openingStockService.listStatus()).extracting(s -> s.product().getId())
                .doesNotContain(productTwo.getId());
        assertThat(receiveStockService.listStatus()).extracting(s -> s.product().getId())
                .doesNotContain(productTwo.getId());
        assertThat(writeOffService.listStatus()).extracting(s -> s.product().getId())
                .doesNotContain(productTwo.getId());
        assertThat(reverseStockService.listStatus()).extracting(s -> s.product().getId())
                .doesNotContain(productTwo.getId());
    }

    @Test
    void rejectsAnotherShopsProductAndChangesNothing() {
        long before = stockMovementRepository.count();

        assertThatThrownBy(() -> productManagementService.deactivate(productTwo.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No product " + productTwo.getId());
        assertThatThrownBy(() -> productManagementService.updateProduct(productTwo.getId(), "Hijacked",
                ProductUnit.UNIT, new BigDecimal("1.00"), BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> openingStockService.recordOpeningBalances(
                Map.of(productTwo.getId(), BigDecimal.TEN), ownerTwo))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> receiveStockService.receive(productTwo.getId(), null, BigDecimal.TEN, ownerTwo))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writeOffService.writeOff(productTwo.getId(), BigDecimal.ONE, "x", ownerTwo))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stockMovementRepository.count()).isEqualTo(before);
        assertThat(productRepository.findById(productTwo.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void rejectsReversingAnotherShopsMovement() {
        assertThatThrownBy(() -> reverseStockService.reverseEntry(movementTwo.getId(), null, ownerTwo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No stock movement " + movementTwo.getId());
    }

    @Test
    void sameProductNameIsAllowedInAnotherShopButNotWithinOne() {
        productManagementService.createProduct("Scoping Oil", ProductUnit.UNIT, new BigDecimal("5.00"),
                BigDecimal.ONE, null);
        assertThatThrownBy(() -> productManagementService.createProduct("scoping oil", ProductUnit.UNIT,
                new BigDecimal("5.00"), BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
