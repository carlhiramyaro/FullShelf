package org.example.backend.product;

import org.example.backend.security.OwnerJwtAuthoritiesConverter;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/owner/products is actually gated by the Clerk-JWT chain
 * (SecurityConfig.ownerFilterChain), and that bad input surfaces as 400 via
 * ProductManagementController's local exception handler rather than the
 * unhandled-500 gap StaffManagementController currently has.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/owner/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPriceReturnsBadRequestNotServerError() throws Exception {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setClerkUserId("clerk_owner_products");
        userRepository.save(owner);

        mockMvc.perform(post("/api/owner/products")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_owner_products")).authorities(ownerJwtAuthoritiesConverter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chicken\",\"unit\":\"KG\",\"price\":0,\"alertLevel\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void creatingThenListingRoundTrips() throws Exception {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setClerkUserId("clerk_owner_products_2");
        userRepository.save(owner);

        mockMvc.perform(post("/api/owner/products")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_owner_products_2")).authorities(ownerJwtAuthoritiesConverter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chicken\",\"unit\":\"KG\",\"price\":35.00,\"alertLevel\":10.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chicken"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/owner/products")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_owner_products_2")).authorities(ownerJwtAuthoritiesConverter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chicken"));
    }
}
