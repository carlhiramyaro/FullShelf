package org.example.backend.security;

import org.example.backend.clerk.ClerkClient;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G7: first login links a pending owner by verified email; assignOwner keeps
 * one active owner per shop. Clerk is a hand-rolled fake (no real API call),
 * and each test uses its own Clerk ids because OwnerLinkService remembers
 * misses for a minute.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerLinkServiceIntegrationTest {

    static class FakeClerkClient implements ClerkClient {
        final Map<String, List<String>> emailsByUser = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public List<String> verifiedEmails(String clerkUserId) {
            calls.add(clerkUserId);
            return emailsByUser.getOrDefault(clerkUserId, List.of());
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeClerkClient fakeClerkClient() {
            return new FakeClerkClient();
        }
    }

    @Autowired
    private OwnerLinkService linkService;
    @Autowired
    private FakeClerkClient clerk;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OwnerJwtAuthoritiesConverter converter;
    @Autowired
    private MockMvc mockMvc;

    private Shop shop;
    private String email;
    private String clerkId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        shop = shopRepository.save(new Shop("Link shop " + unique));
        email = "owner-" + unique + "@example.com";
        clerkId = "user_link_" + unique;
        clerk.calls.clear();
    }

    @Test
    void firstLoginWithAVerifiedMatchingEmailLinksAndActivatesThePendingOwner() {
        User pending = linkService.assignOwner(shop, "Aunt", "+233241234567", email);
        assertThat(pending.isActive()).isFalse();
        assertThat(pending.getClerkUserId()).isNull();
        // Matching ignores case.
        clerk.emailsByUser.put(clerkId, List.of(email.toUpperCase()));

        assertThat(linkService.linkIfPending(clerkId)).isPresent();

        User linked = userRepository.findByClerkUserId(clerkId).orElseThrow();
        assertThat(linked.getId()).isEqualTo(pending.getId());
        assertThat(linked.isActive()).isTrue();
        assertThat(linked.getShop().getId()).isEqualTo(shop.getId());
        assertThat(linked.getPhoneNumber()).isEqualTo("+233241234567");
    }

    @Test
    void anEmailClerkHasNotVerifiedLinksNothing() {
        linkService.assignOwner(shop, "Aunt", null, email);
        // The fake only returns what Clerk reports as verified: nothing here.

        assertThat(linkService.linkIfPending(clerkId)).isEmpty();
        assertThat(userRepository.findByClerkUserId(clerkId)).isEmpty();
    }

    @Test
    void aVerifiedEmailWithNoShopWaitingForItLinksNothing() {
        clerk.emailsByUser.put(clerkId, List.of("stranger-" + clerkId + "@example.com"));

        assertThat(linkService.linkIfPending(clerkId)).isEmpty();
    }

    @Test
    void aSecondClerkUserWithTheSameEmailCannotTakeOverALinkedOwner() {
        linkService.assignOwner(shop, "Aunt", null, email);
        clerk.emailsByUser.put(clerkId, List.of(email));
        linkService.linkIfPending(clerkId);
        String intruder = clerkId + "_intruder";
        clerk.emailsByUser.put(intruder, List.of(email));

        assertThat(linkService.linkIfPending(intruder)).isEmpty();
        assertThat(userRepository.findByClerkUserId(clerkId).orElseThrow().isActive()).isTrue();
    }

    @Test
    void doesNotLinkIntoAShopThatStillHasAnActiveOwner() {
        User current = new User(shop, "Current", UserRole.OWNER);
        current.setClerkUserId(clerkId + "_current");
        userRepository.save(current);
        User pending = new User(shop, "Next", UserRole.OWNER);
        pending.setEmail(email);
        pending.setActive(false);
        userRepository.save(pending);
        clerk.emailsByUser.put(clerkId, List.of(email));

        assertThat(linkService.linkIfPending(clerkId)).isEmpty();
        assertThat(userRepository.findByShopAndRoleAndActiveTrueOrderByName(shop, UserRole.OWNER))
                .extracting(User::getId).containsExactly(current.getId());
    }

    @Test
    void aMissIsNotCheckedAgainWithClerkStraightAway() {
        linkService.linkIfPending(clerkId);
        linkService.linkIfPending(clerkId);

        assertThat(clerk.calls).containsExactly(clerkId);
    }

    @Test
    void anExistingOwnerNeverCallsClerk() {
        User owner = new User(shop, "Aunt", UserRole.OWNER);
        owner.setClerkUserId(clerkId);
        userRepository.save(owner);

        assertThat(converter.convert(jwtWithSubject(clerkId)))
                .extracting(Object::toString).containsExactly("ROLE_OWNER");
        assertThat(clerk.calls).isEmpty();
    }

    @Test
    void theAuthoritiesConverterLinksOnFirstLoginAndGrantsOwner() {
        linkService.assignOwner(shop, "Aunt", null, email);
        clerk.emailsByUser.put(clerkId, List.of(email));

        assertThat(converter.convert(jwtWithSubject(clerkId)))
                .extracting(Object::toString).containsExactly("ROLE_OWNER");
    }

    @Test
    void aFirstRequestThroughTheRealChainLinksAndSeesItsOwnShop() throws Exception {
        linkService.assignOwner(shop, "Link Owner", null, email);
        clerk.emailsByUser.put(clerkId, List.of(email));

        mockMvc.perform(get("/api/owner/me")
                        .with(jwt().jwt(j -> j.subject(clerkId)).authorities(converter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Link Owner"));
    }

    @Test
    void assignOwnerDeactivatesTheCurrentOwnerClearsTheirPhoneAndKeepsOneActive() {
        User old = new User(shop, "Old", UserRole.OWNER);
        old.setClerkUserId(clerkId + "_old");
        old.setEmail(email);
        old.setPhoneNumber("+233200000000");
        userRepository.save(old);

        User next = linkService.assignOwner(shop, "New", "+233241111111", email);

        User reloadedOld = userRepository.findById(old.getId()).orElseThrow();
        assertThat(reloadedOld.isActive()).isFalse();
        assertThat(reloadedOld.getPhoneNumber()).isNull();
        assertThat(next.getId()).isNotEqualTo(old.getId());
        assertThat(userRepository.findByShopAndRoleAndActiveTrueOrderByName(shop, UserRole.OWNER)).isEmpty();

        // The new owner, even with the same email as the old one, links.
        clerk.emailsByUser.put(clerkId, List.of(email));
        assertThat(linkService.linkIfPending(clerkId)).isPresent();
        assertThat(userRepository.findByShopAndRoleAndActiveTrueOrderByName(shop, UserRole.OWNER))
                .extracting(User::getId).containsExactly(next.getId());
    }

    @Test
    void assigningAgainBeforeTheOwnerHasSignedInEditsTheSamePendingRow() {
        User first = linkService.assignOwner(shop, "Typo", null, email);
        User second = linkService.assignOwner(shop, "Fixed", null, "other-" + email);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.findByShopAndRoleAndClerkUserIdIsNull(shop, UserRole.OWNER)).hasSize(1);
        assertThat(second.getEmail()).isEqualTo("other-" + email);
    }

    @Test
    void assignOwnerRejectsABadEmailAndAnEmailAnotherShopIsWaitingFor() {
        assertThatThrownBy(() -> linkService.assignOwner(shop, "Aunt", null, "not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);

        Shop other = shopRepository.save(new Shop("Other link shop"));
        linkService.assignOwner(other, "Someone", null, email);
        assertThatThrownBy(() -> linkService.assignOwner(shop, "Aunt", null, email))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("t").header("alg", "none").subject(subject).build();
    }
}
