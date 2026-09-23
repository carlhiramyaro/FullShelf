package org.example.backend.owner;

import org.example.backend.security.OwnerJwtAuthoritiesConverter;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the Clerk-JWT security chain without a real Clerk instance:
 * SecurityMockMvcRequestPostProcessors.jwt() injects a pre-built JWT straight
 * into the security context, bypassing the real JwtDecoder/JWKS fetch. It
 * builds its own authorities by default though, so each request pins
 * .authorities(ownerJwtAuthoritiesConverter) to route through the app's real
 * converter — otherwise this would only prove the JWT plumbing, not that the
 * users-table role lookup actually gates the response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerJwtAuthoritiesConverter ownerJwtAuthoritiesConverter;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/owner/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtWithNoMatchingUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/owner/me")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_unknown")).authorities(ownerJwtAuthoritiesConverter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtForAStaffUserIsForbidden() throws Exception {
        User staff = new User("Kofi", UserRole.STAFF);
        staff.setClerkUserId("clerk_staff_1");
        userRepository.save(staff);

        mockMvc.perform(get("/api/owner/me")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_staff_1")).authorities(ownerJwtAuthoritiesConverter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtForTheOwnerReturnsHerProfile() throws Exception {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setClerkUserId("clerk_owner_1");
        userRepository.save(owner);

        mockMvc.perform(get("/api/owner/me")
                        .with(jwt().jwt(jwt -> jwt.subject("clerk_owner_1")).authorities(ownerJwtAuthoritiesConverter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aunt Amerley"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }
}
