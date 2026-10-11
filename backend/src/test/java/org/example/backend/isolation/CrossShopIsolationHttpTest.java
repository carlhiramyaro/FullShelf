package org.example.backend.isolation;

import jakarta.persistence.EntityManager;
import org.example.backend.device.Device;
import org.example.backend.device.DeviceRepository;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.sale.SaleService;
import org.example.backend.security.OwnerJwtAuthoritiesConverter;
import org.example.backend.security.TokenHasher;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.staff.StaffSession;
import org.example.backend.staff.StaffSessionRepository;
import org.example.backend.stock.StockMovement;
import org.example.backend.stock.StockMovementService;
import org.example.backend.stock.StockMovementType;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G6: the isolation suite. Two brand-new shops with the same product name, the
 * same staff name and the same receipt number. Callers from shop A (owner and
 * staff, through the real security filters) ask for shop B's ids and must get
 * "not found" and change nothing; lists and aggregates must show only A's data.
 *
 * Both shops are created here, so rows already in shop #1 of the dev database
 * can't interfere. Everything rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrossShopIsolationHttpTest {

    private static final long FIRST_RECEIPT = 910000L;
    private static final String SAME_PRODUCT_NAME = "Isolation Oil";
    private static final String SAME_STAFF_NAME = "Isolation Staff";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private StaffSessionRepository staffSessionRepository;
    @Autowired
    private StockMovementService stockMovementService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private TokenHasher tokenHasher;
    @Autowired
    private CurrentShop currentShop;
    @Autowired
    private OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private ShopFixture a;
    private ShopFixture b;

    /** Everything one shop owns that the probes need to know about. */
    private record ShopFixture(Shop shop, String clerkId, User owner, User staff, Device device,
                               String deviceToken, String sessionToken, Product product,
                               Product secondProduct, StockMovement opening, Long firstReceipt,
                               Long secondReceipt) {
    }

    @BeforeEach
    void setUp() {
        a = buildShop("A", new BigDecimal("2.00"), 1);
        // B's product sits below its alert level and B's second product is negative,
        // so B has something for A's dashboard to wrongly show.
        b = buildShop("B", new BigDecimal("20.00"), 3);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    private ShopFixture buildShop(String label, BigDecimal alertLevel, int firstSaleQuantity) {
        Shop shop = shopRepository.saveAndFlush(new Shop("Isolation Shop " + label));
        // Both shops hand out the same first receipt number, on purpose.
        jdbcTemplate.update("update shops set next_receipt_number = ? where id = ?", FIRST_RECEIPT, shop.getId());

        String clerkId = "clerk_isolation_" + label + "_" + System.nanoTime();
        User owner = new User(shop, "Isolation Owner " + label, UserRole.OWNER);
        owner.setClerkUserId(clerkId);
        owner = userRepository.save(owner);

        User staff = new User(shop, SAME_STAFF_NAME, UserRole.STAFF);
        staff.setPinHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("1234"));
        staff = userRepository.save(staff);

        String deviceToken = "isolation-device-" + label + System.nanoTime();
        Device device = deviceRepository.save(new Device(shop, tokenHasher.hash(deviceToken)));
        String sessionToken = "isolation-session-" + label + System.nanoTime();
        staffSessionRepository.save(new StaffSession(staff, device, tokenHasher.hash(sessionToken)));

        Product product = productRepository.save(new Product(shop, SAME_PRODUCT_NAME, ProductUnit.UNIT,
                new BigDecimal("5.00"), alertLevel));
        Product second = productRepository.save(new Product(shop, "Isolation Second " + label, ProductUnit.UNIT,
                new BigDecimal("5.00"), new BigDecimal("2.00")));

        StockMovement opening = stockMovementService.record(product, new BigDecimal("10"),
                StockMovementType.OPENING, owner, null, null);
        // Balance -1: shows as negative stock on this shop's dashboard.
        stockMovementService.record(second, new BigDecimal("-1"), StockMovementType.WRITE_OFF, owner, null, "x");

        currentShop.set(shop);
        Long first = saleService.confirmSale(List.of(new SaleService.LineRequest(
                product.getId(), new BigDecimal(firstSaleQuantity), BigDecimal.ZERO)), staff.getId()).receiptNumber();
        // A second sale only in B (A never gets a second one), so "A asks for B's
        // receipt" has a number A genuinely doesn't have.
        Long secondReceipt = null;
        if (label.equals("B")) {
            secondReceipt = saleService.confirmSale(List.of(new SaleService.LineRequest(
                    product.getId(), new BigDecimal("2"), BigDecimal.ZERO)), staff.getId()).receiptNumber();
        }
        currentShop.clear();

        return new ShopFixture(shop, clerkId, owner, staff, device, deviceToken, sessionToken, product, second,
                opening, first, secondReceipt);
    }

    // ---- helpers ----

    private RequestPostProcessor ownerOf(ShopFixture f) {
        return jwt().jwt(j -> j.subject(f.clerkId())).authorities(ownerJwtAuthoritiesConverter);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Snapshot of every shop-owned table's rows for one shop, as text. */
    private String snapshot(ShopFixture f) {
        entityManager.flush();
        StringBuilder out = new StringBuilder();
        for (String table : List.of("products", "users", "devices", "sales", "stock_movements", "staff_sessions")) {
            String sql = table.equals("staff_sessions")
                    ? "select coalesce(string_agg(t::text, '|' order by t.id), '') from staff_sessions t "
                            + "join devices d on d.id = t.device_id where d.shop_id = ?"
                    : "select coalesce(string_agg(t::text, '|' order by t.id), '') from " + table
                            + " t where t.shop_id = ?";
            out.append(table).append(':').append(jdbcTemplate.queryForObject(sql, String.class, f.shop().getId()))
                    .append('\n');
        }
        return out.toString();
    }

    // ---- owner of A asks for B's ids ----

    @Test
    void ownerOfAGetsNotFoundForEveryOfBsIdsAndChangesNothing() throws Exception {
        String before = snapshot(b);
        RequestPostProcessor owner = ownerOf(a);
        Long bProduct = b.product().getId();

        String productBody = "{\"name\":\"Hijacked\",\"unit\":\"UNIT\",\"price\":1,\"alertLevel\":1}";
        mockMvc.perform(json(post("/api/owner/products/" + bProduct).with(owner), productBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/owner/products/" + bProduct + "/deactivate").with(owner))
                .andExpect(status().isBadRequest());

        mockMvc.perform(json(post("/api/owner/staff/" + b.staff().getId() + "/reset-pin").with(owner),
                "{\"pin\":\"4321\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/owner/staff/" + b.staff().getId() + "/deactivate").with(owner))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/owner/devices/" + b.device().getId() + "/revoke").with(owner))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/owner/sales/" + b.secondReceipt()).with(owner))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/owner/sales/" + b.secondReceipt() + "/void").with(owner), "{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/owner/stock/reverse/" + bProduct).with(owner))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/owner/stock/reverse").with(owner),
                "{\"movementId\":" + b.opening().getId() + "}")).andExpect(status().isBadRequest());

        mockMvc.perform(json(post("/api/owner/stock/opening").with(owner),
                "{\"entries\":[{\"productId\":" + b.secondProduct().getId() + ",\"quantity\":5}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/owner/stock/receive").with(owner),
                "{\"productId\":" + bProduct + ",\"actualQuantity\":5}")).andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/owner/stock/write-off").with(owner),
                "{\"productId\":" + bProduct + ",\"quantity\":1,\"note\":\"x\"}"))
                .andExpect(status().isBadRequest());

        assertThat(snapshot(b)).isEqualTo(before);
    }

    @Test
    void voidingAReceiptNumberBothShopsHaveOnlyVoidsTheCallersSale() throws Exception {
        assertThat(a.firstReceipt()).isEqualTo(b.firstReceipt());

        mockMvc.perform(json(post("/api/owner/sales/" + a.firstReceipt() + "/void").with(ownerOf(a)), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true))
                .andExpect(jsonPath("$.lines[0].productId").value(a.product().getId()));

        mockMvc.perform(get("/api/owner/sales/" + b.firstReceipt()).with(ownerOf(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(false))
                .andExpect(jsonPath("$.total").value(15.0));
    }

    @Test
    void sameReceiptNumberReturnsTheCallersOwnReceipt() throws Exception {
        mockMvc.perform(get("/api/owner/sales/" + a.firstReceipt()).with(ownerOf(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5.0))
                .andExpect(jsonPath("$.lines[0].productId").value(a.product().getId()));
        mockMvc.perform(get("/api/owner/sales/" + b.firstReceipt()).with(ownerOf(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(15.0))
                .andExpect(jsonPath("$.lines[0].productId").value(b.product().getId()));
    }

    // ---- staff of A asks for B's ids ----

    @Test
    void staffOfAGetsNotFoundForBsIdsAndChangesNothing() throws Exception {
        String before = snapshot(b);

        mockMvc.perform(json(post("/api/staff/sales").header("X-Staff-Session-Token", a.sessionToken()),
                "{\"lines\":[{\"productId\":" + b.product().getId() + ",\"quantity\":1,\"discount\":0}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/staff/sales/" + b.secondReceipt())
                        .header("X-Staff-Session-Token", a.sessionToken()))
                .andExpect(status().isBadRequest());
        // B's staff id on A's device: same rejection as a wrong PIN.
        mockMvc.perform(json(post("/api/staff/login").header("X-Device-Token", a.deviceToken()),
                "{\"staffId\":" + b.staff().getId() + ",\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(snapshot(b)).isEqualTo(before);
    }

    @Test
    void staffReceiptLookupWithASharedNumberReturnsTheCallersReceipt() throws Exception {
        mockMvc.perform(get("/api/staff/sales/" + a.firstReceipt()).header("X-Staff-Session-Token", a.sessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5.0));
    }

    @Test
    void staffRosterAndTodayListShowOnlyTheCallersShop() throws Exception {
        mockMvc.perform(get("/api/staff/roster").header("X-Device-Token", a.deviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(a.staff().getId()));
        mockMvc.perform(get("/api/staff/sales").header("X-Staff-Session-Token", a.sessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.productId==" + a.product().getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.productId==" + b.product().getId() + ")]").isEmpty());
        mockMvc.perform(get("/api/staff/sales/today").header("X-Staff-Session-Token", a.sessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].total").value(5.0));
    }

    // ---- lists and aggregates ----

    @Test
    void ownerListsShowOnlyTheCallersShop() throws Exception {
        RequestPostProcessor owner = ownerOf(a);

        mockMvc.perform(get("/api/owner/products").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id==" + a.product().getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id==" + b.product().getId() + ")]").isEmpty());
        mockMvc.perform(get("/api/owner/staff").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(a.staff().getId()));
        mockMvc.perform(get("/api/owner/devices").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(a.device().getId()));
        mockMvc.perform(get("/api/owner/sales").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].total").value(5.0));

        for (String path : List.of("/api/owner/stock/levels", "/api/owner/stock/opening",
                "/api/owner/stock/receive", "/api/owner/stock/write-off", "/api/owner/stock/reverse")) {
            mockMvc.perform(get(path).with(owner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.productId==" + a.product().getId() + ")]").isNotEmpty())
                    .andExpect(jsonPath("$[?(@.productId==" + b.product().getId() + ")]").isEmpty())
                    .andExpect(jsonPath("$[?(@.productId==" + b.secondProduct().getId() + ")]").isEmpty());
        }
    }

    @Test
    void dashboardCountsOnlyTheCallersShop() throws Exception {
        mockMvc.perform(get("/api/owner/dashboard").with(ownerOf(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesToday").value(5.0))
                .andExpect(jsonPath("$.expectedCash").value(5.0))
                // B has a low product and a negative one; A has neither.
                .andExpect(jsonPath("$.lowStock").isEmpty())
                .andExpect(jsonPath("$.negativeStock[?(@.productId==" + a.secondProduct().getId() + ")]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.negativeStock[?(@.productId==" + b.secondProduct().getId() + ")]").isEmpty());
        mockMvc.perform(get("/api/owner/dashboard").with(ownerOf(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").value(25.0))
                .andExpect(jsonPath("$.lowStock[?(@.productId==" + b.product().getId() + ")]").isNotEmpty());
    }

    @Test
    void dayCloseCountsOnlyTheCallersShop() throws Exception {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/owner/day-close/" + today).with(ownerOf(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSales").value(5.0))
                .andExpect(jsonPath("$.expectedCash").value(5.0))
                .andExpect(jsonPath("$.sales.length()").value(1));
        mockMvc.perform(get("/api/owner/day-close/" + today).with(ownerOf(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").value(25.0))
                .andExpect(jsonPath("$.sales.length()").value(2));
    }

    // ---- guard: a new endpoint can't slip in without isolation coverage ----

    /** Endpoints the tests above send B's ids to, or whose results they check for leaks. */
    private static final Set<String> COVERED = Set.of(
            "GET /api/owner/products",
            "POST /api/owner/products/{id}",
            "POST /api/owner/products/{id}/deactivate",
            "GET /api/owner/staff",
            "POST /api/owner/staff/{id}/reset-pin",
            "POST /api/owner/staff/{id}/deactivate",
            "GET /api/owner/devices",
            "POST /api/owner/devices/{id}/revoke",
            "GET /api/owner/sales",
            "GET /api/owner/sales/{receiptNumber}",
            "POST /api/owner/sales/{receiptNumber}/void",
            "GET /api/owner/stock/levels",
            "GET /api/owner/stock/opening",
            "POST /api/owner/stock/opening",
            "GET /api/owner/stock/receive",
            "POST /api/owner/stock/receive",
            "GET /api/owner/stock/write-off",
            "POST /api/owner/stock/write-off",
            "GET /api/owner/stock/reverse",
            "GET /api/owner/stock/reverse/{productId}",
            "POST /api/owner/stock/reverse",
            "GET /api/owner/dashboard",
            "GET /api/owner/day-close/{date}",
            "GET /api/staff/roster",
            "POST /api/staff/login",
            "GET /api/staff/sales",
            "POST /api/staff/sales",
            "GET /api/staff/sales/today",
            "GET /api/staff/sales/{receiptNumber}");

    /** Endpoints that take no id and read or write only the caller's own row. */
    private static final Set<String> EXEMPT = Set.of(
            "GET /api/owner/me",            // the caller's own owner row
            "POST /api/owner/me/phone",     // the caller's own phone number
            "POST /api/owner/devices/pairing-code", // creates a code in the caller's own shop
            "POST /api/owner/products",     // creates in the caller's own shop; shop never read from the body
            "POST /api/owner/staff",        // same
            "POST /api/staff/logout");      // ends the caller's own session

    @Test
    void everyOwnerAndStaffEndpointIsCoveredOrExplicitlyExempt() {
        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/owner") && !pattern.startsWith("/api/staff")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    actual.add(method + " " + pattern);
                }
            }
        }

        Set<String> known = new TreeSet<>(COVERED);
        known.addAll(EXEMPT);
        assertThat(actual)
                .as("New endpoint? Add an isolation probe and list it in COVERED, or justify it in EXEMPT.")
                .isEqualTo(known);
    }
}
