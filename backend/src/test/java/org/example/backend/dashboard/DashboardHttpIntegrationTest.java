package org.example.backend.dashboard;

import org.example.backend.security.OwnerJwtAuthoritiesConverter;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/owner/dashboard is gated by the Clerk-JWT chain, same check
 * as ProductManagementControllerTest — DashboardIntegrationTest already
 * covers the aggregation logic itself via DashboardService directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/owner/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerGetsTheSummaryShape() throws Exception {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setClerkUserId("clerk_owner_dashboard");
        userRepository.save(owner);

        mockMvc.perform(get("/api/owner/dashboard")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_owner_dashboard")).authorities(ownerJwtAuthoritiesConverter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesToday").exists())
                .andExpect(jsonPath("$.discountsToday").exists())
                .andExpect(jsonPath("$.expectedCash").exists())
                .andExpect(jsonPath("$.lowStock").isArray())
                .andExpect(jsonPath("$.negativeStock").isArray());
    }
}
