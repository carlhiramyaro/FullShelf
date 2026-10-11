package org.example.backend.admin;

import org.example.backend.security.AdminJwtAuthoritiesConverter;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G8: the admin chain's access rules and the shop list / create / re-link operations. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.admin-clerk-user-id=admin_test_id")
class AdminShopIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String uniqueEmail() {
        return "owner-" + UUID.randomUUID() + "@example.com";
    }

    private static String createBody(String shopName, String phone, String email) {
        return "{\"shopName\":\"" + shopName + "\",\"ownerName\":\"Ama\",\"phone\":"
                + (phone == null ? "null" : "\"" + phone + "\"") + ",\"email\":\"" + email + "\"}";
    }

    private static Jwt tokenFor(String subject) {
        return Jwt.withTokenValue("t").header("alg", "none").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    // ---- who may call /api/admin ----

    @Test
    void noTokenIs401AndAnOwnerIs403() throws Exception {
        mockMvc.perform(get("/api/admin/shops")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/shops")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGetsIn() throws Exception {
        mockMvc.perform(get("/api/admin/me").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void onlyTheConfiguredClerkIdIsGrantedAdmin() {
        assertThat(new AdminJwtAuthoritiesConverter("admin_test_id").convert(tokenFor("admin_test_id")))
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(new AdminJwtAuthoritiesConverter("admin_test_id").convert(tokenFor("someone_else"))).isEmpty();
    }

    @Test
    void anUnsetAdminIdGrantsNobody() {
        assertThat(new AdminJwtAuthoritiesConverter("").convert(tokenFor(""))).isEmpty();
        assertThat(new AdminJwtAuthoritiesConverter("  ").convert(tokenFor("anyone"))).isEmpty();
    }

    // ---- create ----

    @Test
    void createShopMakesShopAndPendingOwner() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/admin/shops").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Admin Test Shop", "+233241234567", email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Test Shop"))
                .andExpect(jsonPath("$.activeStaff").value(0))
                .andExpect(jsonPath("$.owner.status").value("pending"))
                .andExpect(jsonPath("$.owner.email").value(email));

        Shop shop = shopRepository.findAll().stream()
                .filter(s -> s.getName().equals("Admin Test Shop")).findFirst().orElseThrow();
        User owner = userRepository.findByShopAndRoleAndClerkUserIdIsNull(shop, UserRole.OWNER).get(0);
        assertThat(owner.isActive()).isFalse();
        assertThat(owner.getPhoneNumber()).isEqualTo("+233241234567");
        assertThat(shopRepository.nextReceiptNumber(shop.getId())).isEqualTo(1000L);
    }

    // Outside the class-wide test transaction, so the service's own rollback is what's tested.
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void badEmailOrPhoneOrNameCreatesNothing() throws Exception {
        long before = shopRepository.count();
        mockMvc.perform(post("/api/admin/shops").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Bad Email Shop", null, "not-an-email")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/shops").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Bad Phone Shop", "0241234567", uniqueEmail())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/shops").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("  ", null, uniqueEmail())))
                .andExpect(status().isBadRequest());
        assertThat(shopRepository.count()).isEqualTo(before);
    }

    // ---- re-link ----

    @Test
    void relinkDeactivatesTheActiveOwnerAndClearsTheirPhone() throws Exception {
        Shop shop = shopRepository.save(new Shop("Relink Shop"));
        User old = new User(shop, "Old Owner", UserRole.OWNER);
        old.setClerkUserId("clerk_" + UUID.randomUUID());
        old.setPhoneNumber("+233200000000");
        userRepository.saveAndFlush(old);

        String email = uniqueEmail();
        mockMvc.perform(post("/api/admin/shops/" + shop.getId() + "/owner").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"New Owner\",\"phone\":null,\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.name").value("New Owner"))
                .andExpect(jsonPath("$.owner.status").value("pending"));

        User reloaded = userRepository.findById(old.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getPhoneNumber()).isNull();
    }

    @Test
    void relinkTwiceBeforeSignInEditsThePendingRow() throws Exception {
        Shop shop = shopRepository.save(new Shop("Typo Shop"));
        mockMvc.perform(post("/api/admin/shops/" + shop.getId() + "/owner").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Ama\",\"email\":\"" + uniqueEmail() + "\"}")).andExpect(status().isOk());
        String fixed = uniqueEmail();
        mockMvc.perform(post("/api/admin/shops/" + shop.getId() + "/owner").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"Ama\",\"email\":\"" + fixed + "\"}")).andExpect(status().isOk());

        var pending = userRepository.findByShopAndRoleAndClerkUserIdIsNull(shop, UserRole.OWNER);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEmail()).isEqualTo(fixed);
    }

    @Test
    void anEmailAlreadyWaitingInAnotherShopIsRejected() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/admin/shops").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody("First Waiting Shop", null, email))).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/shops").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Second Waiting Shop", null, email)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void relinkOfAMissingShopIs400() throws Exception {
        mockMvc.perform(post("/api/admin/shops/999999999/owner").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ama\",\"email\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- list ----

    @Test
    void listShowsOwnerStatusAndPerShopActiveStaffCounts() throws Exception {
        Shop a = shopRepository.save(new Shop("List Shop A"));
        Shop b = shopRepository.save(new Shop("List Shop B"));
        User activeOwner = new User(a, "Owner A", UserRole.OWNER);
        activeOwner.setClerkUserId("clerk_" + UUID.randomUUID());
        userRepository.save(activeOwner);
        userRepository.save(new User(a, "Staff 1", UserRole.STAFF));
        userRepository.save(new User(a, "Staff 2", UserRole.STAFF));
        User gone = new User(a, "Staff Gone", UserRole.STAFF);
        gone.setActive(false);
        userRepository.save(gone);
        userRepository.save(new User(b, "Other Shop Staff", UserRole.STAFF));
        userRepository.flush();

        mockMvc.perform(get("/api/admin/shops").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + a.getId() + ")].activeStaff").value(2))
                .andExpect(jsonPath("$[?(@.id==" + a.getId() + ")].owner.status").value("active"))
                .andExpect(jsonPath("$[?(@.id==" + b.getId() + ")].activeStaff").value(1))
                .andExpect(jsonPath("$[?(@.id==" + b.getId() + ")].owner").value((Object) null));
    }

    // ---- guard: a new admin endpoint must be listed (and so tested) here ----

    private static final Set<String> KNOWN = Set.of(
            "GET /api/admin/me",
            "GET /api/admin/shops",
            "POST /api/admin/shops",
            "POST /api/admin/shops/{shopId}/owner");

    @Test
    void everyAdminEndpointIsListed() {
        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/admin")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    actual.add(method + " " + pattern);
                }
            }
        }
        assertThat(actual).as("New admin endpoint? Add a test and list it in KNOWN.").isEqualTo(new TreeSet<>(KNOWN));
    }
}
