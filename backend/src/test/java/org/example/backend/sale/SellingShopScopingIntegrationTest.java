package org.example.backend.sale;

import org.example.backend.device.Device;
import org.example.backend.device.DevicePairingService;
import org.example.backend.device.DeviceRepository;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.shop.TestShops;
import org.example.backend.staff.StaffAuthService;
import org.example.backend.staff.StaffManagementService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G4 smoke check: the caller is in shop 1; the sale, staff member and device
 * belong to another shop. Selling, staff and device services must treat them
 * as not existing. The full cross-shop suite is G6.
 */
@SpringBootTest
@Transactional
class SellingShopScopingIntegrationTest {

    @Autowired
    private CurrentShop currentShop;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SaleService saleService;
    @Autowired
    private SaleQueryService saleQueryService;
    @Autowired
    private VoidSaleService voidSaleService;
    @Autowired
    private StaffManagementService staffManagementService;
    @Autowired
    private StaffAuthService staffAuthService;
    @Autowired
    private DevicePairingService devicePairingService;

    private Shop two;
    private User ownerTwo;
    private User staffTwo;
    private Device deviceTwo;
    private Long receiptTwo;

    @BeforeEach
    void setUp() {
        two = shopRepository.saveAndFlush(new Shop("Selling Shop Two"));
        ownerTwo = userRepository.save(new User(two, "Selling Owner Two", UserRole.OWNER));
        staffTwo = new User(two, "Selling Staff Two", UserRole.STAFF);
        staffTwo.setPinHash("x");
        staffTwo = userRepository.save(staffTwo);
        deviceTwo = deviceRepository.save(new Device(two, "selling-scoping-hash"));
        Product productTwo = productRepository.save(
                new Product(two, "Selling Oil", ProductUnit.UNIT, new BigDecimal("5.00"), new BigDecimal("2.00")));

        // Shop #1 in the local dev database may already hold receipt 1000.
        // Numbers repeating across shops is intended (ShopSchemaIntegrationTest),
        // but here a number only shop two has makes "not found" unambiguous.
        jdbcTemplate.update("update shops set next_receipt_number = 900000 where id = ?", two.getId());
        currentShop.set(two);
        receiptTwo = saleService.confirmSale(
                List.of(new SaleService.LineRequest(productTwo.getId(), BigDecimal.ONE, BigDecimal.ZERO)),
                staffTwo.getId()).receiptNumber();
        currentShop.set(TestShops.ONE);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    @Test
    void salesListsAndLookupsExcludeAnotherShop() {
        assertThat(saleQueryService.listAll()).extracting(SaleQueryService.SaleSummary::receiptNumber)
                .doesNotContain(receiptTwo);
        assertThat(saleQueryService.listToday()).extracting(SaleQueryService.SaleSummary::receiptNumber)
                .doesNotContain(receiptTwo);
        assertThatThrownBy(() -> saleQueryService.findByReceiptNumber(receiptTwo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void voidingAnotherShopsSaleFailsAndChangesNothing() {
        assertThatThrownBy(() -> voidSaleService.voidSale(receiptTwo, null, ownerTwo))
                .isInstanceOf(IllegalArgumentException.class);

        currentShop.set(two);
        assertThat(saleQueryService.findByReceiptNumber(receiptTwo).voided()).isFalse();
    }

    @Test
    void sellingAsAnotherShopsStaffFails() {
        Product mine = productRepository.save(
                new Product(TestShops.ONE, "Selling Mine", ProductUnit.UNIT, new BigDecimal("5.00"),
                        new BigDecimal("2.00")));
        assertThatThrownBy(() -> saleService.confirmSale(
                List.of(new SaleService.LineRequest(mine.getId(), BigDecimal.ONE, BigDecimal.ZERO)),
                staffTwo.getId())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void staffManagementAndRosterExcludeAnotherShop() {
        assertThat(staffManagementService.listStaff()).extracting(User::getId).doesNotContain(staffTwo.getId());
        assertThat(staffAuthService.roster()).extracting(User::getId).doesNotContain(staffTwo.getId());
        assertThatThrownBy(() -> staffManagementService.resetPin(staffTwo.getId(), "4321"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> staffManagementService.deactivate(staffTwo.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(userRepository.findById(staffTwo.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void revokingAnotherShopsDeviceFailsAndChangesNothing() {
        assertThatThrownBy(() -> devicePairingService.revokeDevice(deviceTwo.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(deviceRepository.findById(deviceTwo.getId()).orElseThrow().isRevoked()).isFalse();
        assertThat(deviceRepository.findByShopOrderById(TestShops.ONE)).extracting(Device::getId)
                .doesNotContain(deviceTwo.getId());
    }
}
