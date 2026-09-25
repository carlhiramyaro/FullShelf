package org.example.backend.sale;

import jakarta.persistence.EntityManager;
import org.example.backend.device.Device;
import org.example.backend.device.DeviceRepository;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.security.TokenHasher;
import org.example.backend.staff.StaffSession;
import org.example.backend.staff.StaffSessionRepository;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the one behaviour that lives in SaleController itself rather than
 * SaleQueryService (which SaleQueryIntegrationTest already exercises
 * directly): a staff session getting a 400, not the receipt, for a sale from
 * an earlier day. Builds the staff session directly (device + session rows,
 * hashed token) rather than through the pairing/login HTTP flow, since that
 * flow is already covered end to end by StaffAccessHttpIntegrationTest and
 * isn't what this test is about.
 *
 * MockMvc runs the request on the same thread and transaction as the test
 * method, so without flushing and clearing the persistence context after
 * confirmSale(), the GET below would see the same stale managed Sale
 * instance the write produced (receiptNumber still null) instead of a fresh
 * read — see the longer explanation on SaleQueryIntegrationTest. A real
 * POST-then-GET never shares a persistence context, so this is purely to
 * make the test honestly exercise the query path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleQueryHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private StaffSessionRepository staffSessionRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private SaleService saleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private String staffSessionToken() {
        Device device = deviceRepository.save(new Device(tokenHasher.hash("device-" + System.nanoTime())));
        User staff = userRepository.save(new User("Sale Query Http Test Staff", UserRole.STAFF));
        String rawToken = tokenHasher.newRawToken();
        staffSessionRepository.save(new StaffSession(staff, device, tokenHasher.hash(rawToken)));
        return rawToken;
    }

    @Test
    void staffCanFetchTodaysReceipt() throws Exception {
        String sessionToken = staffSessionToken();
        Product oil = productRepository.save(new Product(
                "Sale Query Http Test Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));
        User anySaleStaff = userRepository.save(new User("Sale Query Http Test Seller", UserRole.STAFF));
        SaleService.SaleResult result = saleService.confirmSale(
                List.of(new SaleService.LineRequest(oil.getId(), BigDecimal.ONE, null)), anySaleStaff.getId());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/staff/sales/" + result.receiptNumber())
                        .header("X-Staff-Session-Token", sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value(result.receiptNumber()));
    }

    @Test
    void staffCannotFetchAnOlderReceipt() throws Exception {
        String sessionToken = staffSessionToken();
        Product oil = productRepository.save(new Product(
                "Sale Query Http Test Oil 2", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));
        User anySaleStaff = userRepository.save(new User("Sale Query Http Test Seller 2", UserRole.STAFF));
        SaleService.SaleResult result = saleService.confirmSale(
                List.of(new SaleService.LineRequest(oil.getId(), BigDecimal.ONE, null)), anySaleStaff.getId());
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("update sales set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), result.sale().getId());
        entityManager.clear();

        mockMvc.perform(get("/api/staff/sales/" + result.receiptNumber())
                        .header("X-Staff-Session-Token", sessionToken))
                .andExpect(status().isBadRequest());
    }
}
