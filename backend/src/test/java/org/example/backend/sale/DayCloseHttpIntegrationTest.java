package org.example.backend.sale;

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
 * Confirms /api/owner/day-close/{date} is gated by the Clerk-JWT chain (same
 * check as DashboardHttpIntegrationTest) and that a malformed date gets a
 * 400 from DayCloseController's own @ExceptionHandler rather than a raw 500
 * — DayCloseIntegrationTest already covers the query logic itself directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DayCloseHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/owner/day-close/2026-03-10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerGetsTheDayCloseShape() throws Exception {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setClerkUserId("clerk_owner_day_close");
        userRepository.save(owner);

        mockMvc.perform(get("/api/owner/day-close/2026-03-10")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_owner_day_close")).authorities(ownerJwtAuthoritiesConverter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSales").exists())
                .andExpect(jsonPath("$.totalDiscounts").exists())
                .andExpect(jsonPath("$.expectedCash").exists())
                .andExpect(jsonPath("$.sales").isArray());
    }

    @Test
    void malformedDateIsRejectedWithBadRequestNotServerError() throws Exception {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setClerkUserId("clerk_owner_day_close_bad_date");
        userRepository.save(owner);

        mockMvc.perform(get("/api/owner/day-close/not-a-date")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_owner_day_close_bad_date")).authorities(ownerJwtAuthoritiesConverter)))
                .andExpect(status().isBadRequest());
    }
}
