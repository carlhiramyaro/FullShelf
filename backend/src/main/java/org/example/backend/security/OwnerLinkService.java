package org.example.backend.security;

import org.example.backend.clerk.ClerkClient;
import org.example.backend.shop.Shop;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects an owner's Clerk login to their shop.
 *
 * An owner starts as a pending row: role OWNER, an email, no Clerk id,
 * inactive. The first login whose Clerk user has a verified email matching a
 * pending row fills in the Clerk id and activates it ({@link #linkIfPending}).
 * {@link #assignOwner} creates or replaces that pending row for a shop; G8's
 * admin screen calls it both to create a shop's owner and to re-link.
 *
 * A shop has at most one active owner; this class is where that is enforced.
 */
@Service
public class OwnerLinkService {

    private static final Logger log = LoggerFactory.getLogger(OwnerLinkService.class);

    // A login with no matching shop would otherwise cost a Clerk API call on
    // every request it makes. After a miss we don't ask again for this long.
    private static final Duration RETRY_AFTER_MISS = Duration.ofSeconds(60);
    private static final int MAX_REMEMBERED_MISSES = 10_000;

    private final UserRepository userRepository;
    private final ClerkClient clerkClient;
    private final TransactionTemplate transaction;
    private final Map<String, Instant> recentMisses = new ConcurrentHashMap<>();

    public OwnerLinkService(UserRepository userRepository, ClerkClient clerkClient,
                            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.clerkClient = clerkClient;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Called for a Clerk user with no owner row yet. Empty if nothing matches. */
    public Optional<User> linkIfPending(String clerkUserId) {
        Instant lastMiss = recentMisses.get(clerkUserId);
        if (lastMiss != null && lastMiss.plus(RETRY_AFTER_MISS).isAfter(Instant.now())) {
            return Optional.empty();
        }

        // The HTTP call happens before the transaction opens, so a slow Clerk
        // doesn't hold a database connection.
        List<String> emails = clerkClient.verifiedEmails(clerkUserId);

        Optional<User> linked;
        try {
            linked = transaction.execute(status -> link(clerkUserId, emails));
        } catch (DataIntegrityViolationException e) {
            // Two first requests raced and the other one linked this Clerk user.
            linked = userRepository.findByClerkUserId(clerkUserId);
        }

        if (linked.isPresent()) {
            recentMisses.remove(clerkUserId);
        } else {
            if (recentMisses.size() >= MAX_REMEMBERED_MISSES) {
                recentMisses.clear();
            }
            recentMisses.put(clerkUserId, Instant.now());
        }
        return linked;
    }

    private Optional<User> link(String clerkUserId, List<String> verifiedEmails) {
        Optional<User> existing = userRepository.findByClerkUserId(clerkUserId);
        if (existing.isPresent()) {
            return existing;
        }
        for (String email : verifiedEmails) {
            Optional<User> pending = userRepository
                    .findFirstByEmailIgnoreCaseAndRoleAndClerkUserIdIsNull(email, UserRole.OWNER);
            if (pending.isEmpty()) {
                continue;
            }
            User owner = pending.get();
            if (!userRepository.findByShopAndRoleAndActiveTrueOrderByName(owner.getShop(), UserRole.OWNER).isEmpty()) {
                // The shop still has an active owner; replacing one is assignOwner's job.
                log.warn("Not linking Clerk user {}: shop {} already has an active owner",
                        clerkUserId, owner.getShop().getId());
                continue;
            }
            owner.setClerkUserId(clerkUserId);
            owner.setActive(true);
            userRepository.saveAndFlush(owner);
            log.info("Linked Clerk user {} as owner of shop {}", clerkUserId, owner.getShop().getId());
            return Optional.of(owner);
        }
        return Optional.empty();
    }

    /**
     * Makes {@code email} the shop's next owner. The current active owner, if
     * any, is deactivated (never deleted: devices and stock movements point at
     * it) and loses their phone number, which belonged to them. The new owner
     * stays pending until they sign in with that email. Calling it again before
     * they have, with different details, edits the same pending row.
     */
    public User assignOwner(Shop shop, String name, String phoneNumber, String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Owner name is required");
        }
        if (userRepository.existsByEmailIgnoreCaseAndRoleAndClerkUserIdIsNullAndShopNot(
                normalizedEmail, UserRole.OWNER, shop)) {
            throw new IllegalArgumentException("Another shop is already waiting for an owner with that email");
        }

        for (User current : userRepository.findByShopAndRoleAndActiveTrueOrderByName(shop, UserRole.OWNER)) {
            current.setActive(false);
            current.setPhoneNumber(null);
            log.info("Deactivated owner {} of shop {}", current.getId(), shop.getId());
        }

        List<User> waiting = userRepository.findByShopAndRoleAndClerkUserIdIsNull(shop, UserRole.OWNER);
        User pending = waiting.isEmpty() ? new User(shop, name.trim(), UserRole.OWNER) : waiting.get(0);
        pending.setName(name.trim());
        pending.setEmail(normalizedEmail);
        pending.setPhoneNumber(phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim());
        pending.setActive(false);
        return userRepository.save(pending);
    }
}
