package org.example.backend.shop;

import org.example.backend.device.Device;
import org.example.backend.device.DeviceRepository;
import org.example.backend.product.ProductRepository;
import org.example.backend.security.OwnerJwtAuthoritiesConverter;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G2: the shop comes from the authenticated identity. Owner routes take it
 * from the owner's row, staff routes from the device and the staff session,
 * and a session that doesn't line up with its device is rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShopContextHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private StaffSessionRepository staffSessionRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TokenHasher tokenHasher;
    @Autowired
    private CurrentShop currentShop;
    @Autowired
    private OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;

    @Test
    void currentShopThrowsWhenNothingSetIt() {
        assertThatThrownBy(() -> currentShop.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ownerCreatesProductsInTheirOwnShopNotShopOne() throws Exception {
        Shop other = shopRepository.save(new Shop("Other shop"));
        User owner = new User(other, "Other owner", UserRole.OWNER);
        owner.setClerkUserId("clerk_g2_other_owner");
        userRepository.save(owner);

        mockMvc.perform(post("/api/owner/products")
                        .with(jwt().jwt(j -> j.subject("clerk_g2_other_owner")).authorities(ownerJwtAuthoritiesConverter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G2 Rice\",\"unit\":\"KG\",\"price\":10,\"alertLevel\":1}"))
                .andExpect(status().isOk());

        assertThat(productRepository.findAll())
                .filteredOn(p -> p.getName().equals("G2 Rice"))
                .singleElement()
                .satisfies(p -> assertThat(p.getShop().getId()).isEqualTo(other.getId()));
        // The filter cleans up after the request.
        assertThatThrownBy(() -> currentShop.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staffSessionOnAMatchingDeviceIsAccepted() throws Exception {
        Shop shop = shopRepository.save(new Shop("Shop A"));
        String token = sessionToken(shop, shop, false);

        mockMvc.perform(post("/api/staff/logout").header("X-Staff-Session-Token", token))
                .andExpect(status().isOk());
    }

    @Test
    void staffSessionWhoseUserBelongsToAnotherShopThanTheDeviceIsRejected() throws Exception {
        Shop a = shopRepository.save(new Shop("Shop A"));
        Shop b = shopRepository.save(new Shop("Shop B"));
        String token = sessionToken(a, b, false);

        mockMvc.perform(post("/api/staff/logout").header("X-Staff-Session-Token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffSessionOnARevokedDeviceIsRejected() throws Exception {
        Shop shop = shopRepository.save(new Shop("Shop A"));
        String token = sessionToken(shop, shop, true);

        mockMvc.perform(post("/api/staff/logout").header("X-Staff-Session-Token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotLogInOnADeviceFromAnotherShop() throws Exception {
        Shop a = shopRepository.save(new Shop("Shop A"));
        Shop b = shopRepository.save(new Shop("Shop B"));
        User staff = new User(a, "Kofi", UserRole.STAFF);
        staff.setPinHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("1234"));
        userRepository.save(staff);
        String deviceToken = "g2-device-" + System.nanoTime();
        deviceRepository.save(new Device(b, tokenHasher.hash(deviceToken)));

        mockMvc.perform(post("/api/staff/login")
                        .header("X-Device-Token", deviceToken)
                        .contentType("application/json")
                        .content("{\"staffId\":" + staff.getId() + ",\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String sessionToken(Shop userShop, Shop deviceShop, boolean revoked) {
        User staff = userRepository.save(new User(userShop, "Kofi", UserRole.STAFF));
        Device device = new Device(deviceShop, tokenHasher.hash("g2-device-" + System.nanoTime()));
        device.setRevoked(revoked);
        deviceRepository.save(device);
        String raw = "g2-session-" + System.nanoTime();
        staffSessionRepository.save(new StaffSession(staff, device, tokenHasher.hash(raw)));
        return raw;
    }
}
