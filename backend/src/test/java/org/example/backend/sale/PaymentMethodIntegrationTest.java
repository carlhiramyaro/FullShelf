package org.example.backend.sale;

import jakarta.persistence.EntityManager;
import org.example.backend.device.Device;
import org.example.backend.device.DeviceRepository;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.security.TokenHasher;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.TestShops;
import org.example.backend.staff.StaffSession;
import org.example.backend.staff.StaffSessionRepository;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** I1: the payment method is stored, defaulted, validated, and splits the totals. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentMethodIntegrationTest {

    // A day with no other data, so totals can be asserted exactly.
    private static final LocalDate DAY = LocalDate.of(2019, 5, 6);

    @Autowired private CurrentShop currentShop;
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private StaffSessionRepository staffSessionRepository;
    @Autowired private TokenHasher tokenHasher;
    @Autowired private SaleService saleService;
    @Autowired private VoidSaleService voidSaleService;
    @Autowired private SaleQueryService saleQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void setCurrentShop() {
        currentShop.set(TestShops.ONE);
    }

    @AfterEach
    void clearCurrentShop() {
        currentShop.clear();
    }

    private Product oil() {
        return productRepository.save(new Product(TestShops.ONE,
                "Payment Test Oil " + System.nanoTime(), ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));
    }

    private void backdate(SaleService.SaleResult sale) {
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("update sales set created_at = ? where id = ?",
                Timestamp.from(DAY.atTime(12, 0).toInstant(ZoneOffset.UTC)), sale.sale().getId());
        entityManager.clear();
    }

    @Test
    void storesTheMethodAndDefaultsToCash() {
        User staff = userRepository.save(new User(TestShops.ONE, "Payment Test Staff", UserRole.STAFF));
        Product oil = oil();
        List<SaleService.LineRequest> lines = List.of(new SaleService.LineRequest(oil.getId(), BigDecimal.ONE, null));

        assertThat(saleService.confirmSale(lines, staff.getId(), PaymentMethod.MOMO).sale().getPaymentMethod())
                .isEqualTo(PaymentMethod.MOMO);
        assertThat(saleService.confirmSale(lines, staff.getId()).sale().getPaymentMethod())
                .isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void parseDefaultsMissingToCashAndRejectsUnknown() {
        assertThat(PaymentMethod.parse(null)).isEqualTo(PaymentMethod.CASH);
        assertThat(PaymentMethod.parse("momo")).isEqualTo(PaymentMethod.MOMO);
        assertThatThrownBy(() -> PaymentMethod.parse("CARD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentMethod.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalsSplitByMethodAndAVoidedMomoSaleLeavesCashAlone() {
        User staff = userRepository.save(new User(TestShops.ONE, "Payment Test Staff", UserRole.STAFF));
        User owner = userRepository.save(new User(TestShops.ONE, "Payment Test Owner", UserRole.OWNER));
        Product oil = oil();

        SaleService.SaleResult cash = saleService.confirmSale(
                List.of(new SaleService.LineRequest(oil.getId(), new BigDecimal("2"), new BigDecimal("5.00"))),
                staff.getId(), PaymentMethod.CASH); // 45.00
        backdate(cash);
        SaleService.SaleResult momo = saleService.confirmSale(
                List.of(new SaleService.LineRequest(oil.getId(), new BigDecimal("3"), null)),
                staff.getId(), PaymentMethod.MOMO); // 75.00
        backdate(momo);
        SaleService.SaleResult voidedMomo = saleService.confirmSale(
                List.of(new SaleService.LineRequest(oil.getId(), new BigDecimal("1"), null)),
                staff.getId(), PaymentMethod.MOMO);
        backdate(voidedMomo);
        voidSaleService.voidSale(voidedMomo.receiptNumber(), null,
                userRepository.findById(owner.getId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        SaleQueryService.SaleTotals totals = saleQueryService.dayTotals(DAY);
        assertThat(totals.salesCash()).isEqualByComparingTo("45.00");
        assertThat(totals.salesMomo()).isEqualByComparingTo("75.00");
        assertThat(totals.expectedCash()).isEqualByComparingTo("45.00");
        assertThat(totals.expectedMomo()).isEqualByComparingTo("75.00");
        assertThat(totals.discounts()).isEqualByComparingTo("5.00");
        assertThat(totals.grossSales()).isEqualByComparingTo("125.00");

        assertThat(saleQueryService.listForDay(DAY)).extracting(SaleQueryService.DaySaleSummary::paymentMethod)
                .containsExactlyInAnyOrder("CASH", "MOMO", "MOMO");
        assertThat(saleQueryService.findByReceiptNumber(momo.receiptNumber()).paymentMethod()).isEqualTo("MOMO");
    }

    @Test
    void theSalesEndpointStoresMomoDefaultsToCashAndRejectsAnUnknownMethod() throws Exception {
        Device device = deviceRepository.save(new Device(TestShops.ONE, tokenHasher.hash("device-" + System.nanoTime())));
        User staff = userRepository.save(new User(TestShops.ONE, "Payment Http Test Staff", UserRole.STAFF));
        String token = tokenHasher.newRawToken();
        staffSessionRepository.save(new StaffSession(staff, device, tokenHasher.hash(token)));
        Product oil = oil();
        String lines = "\"lines\":[{\"productId\":" + oil.getId() + ",\"quantity\":1}]";

        mockMvc.perform(post("/api/staff/sales").header("X-Staff-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{" + lines + ",\"paymentMethod\":\"MOMO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentMethod").value("MOMO"));
        mockMvc.perform(post("/api/staff/sales").header("X-Staff-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{" + lines + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentMethod").value("CASH"));
        mockMvc.perform(post("/api/staff/sales").header("X-Staff-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{" + lines + ",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isBadRequest());
    }
}
