package org.example.backend.security;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Keeps exactly one active owner account, linked to the Clerk user id in
 * OWNER_CLERK_USER_ID. There is exactly one owner for the life of this app,
 * so this is a targeted env-var bootstrap rather than a self-service
 * provisioning flow — letting anyone who completes Clerk login become an
 * owner would be a real hole, and an invite/approve flow is overkill for a
 * single permanent user. Each environment (local/staging/prod) sets its own
 * OWNER_CLERK_USER_ID.
 *
 * If the configured id changes (a new Clerk instance, or the real owner
 * replacing the developer's login at go-live), the existing owner row is
 * relinked to the new id rather than a second owner being inserted, and any
 * other owner rows are deactivated. Otherwise two active owners would
 * coexist, and anything that picks "the" owner by role (SmsNotifier) could
 * pick the stale one.
 */
@Component
public class OwnerBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrapRunner.class);

    private final UserRepository userRepository;
    private final String ownerClerkUserId;

    public OwnerBootstrapRunner(UserRepository userRepository,
                                 @Value("${app.owner-clerk-user-id:}") String ownerClerkUserId) {
        this.userRepository = userRepository;
        this.ownerClerkUserId = ownerClerkUserId;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ownerClerkUserId == null || ownerClerkUserId.isBlank()) {
            log.warn("OWNER_CLERK_USER_ID not set — no owner account will be able to authenticate");
            return;
        }

        User owner = userRepository.findByClerkUserId(ownerClerkUserId).orElse(null);
        if (owner != null) {
            if (!owner.isActive()) {
                owner.setActive(true);
                log.info("Reactivated owner user for Clerk id {}", ownerClerkUserId);
            }
        } else {
            List<User> activeOwners = userRepository.findByRoleAndActiveTrueOrderByIdAsc(UserRole.OWNER);
            if (activeOwners.isEmpty()) {
                owner = new User("Owner", UserRole.OWNER);
                owner.setClerkUserId(ownerClerkUserId);
                userRepository.save(owner);
                log.info("Bootstrapped owner user for Clerk id {}", ownerClerkUserId);
            } else {
                // Same person's seat, new login: keep the row (devices and
                // stock movements reference it) but drop the old phone
                // number, which belonged to whoever held the seat before.
                owner = activeOwners.get(0);
                log.info("Relinking owner user {} from Clerk id {} to {}",
                        owner.getId(), owner.getClerkUserId(), ownerClerkUserId);
                owner.setClerkUserId(ownerClerkUserId);
                owner.setPhoneNumber(null);
            }
        }

        for (User other : userRepository.findByRoleAndActiveTrueOrderByIdAsc(UserRole.OWNER)) {
            if (!other.getId().equals(owner.getId())) {
                other.setActive(false);
                log.info("Deactivated stale owner user {} (Clerk id {})", other.getId(), other.getClerkUserId());
            }
        }
    }
}
