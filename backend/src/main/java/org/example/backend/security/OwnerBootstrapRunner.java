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

/**
 * Links the one permanent owner account to her Clerk user id on startup.
 * There is exactly one owner for the life of this app, so this is a targeted
 * env-var bootstrap rather than a self-service provisioning flow — letting
 * anyone who completes Clerk login become an owner would be a real hole, and
 * an invite/approve flow is overkill for a single permanent user. Each
 * environment (local/staging/prod) sets its own OWNER_CLERK_USER_ID.
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
    public void run(ApplicationArguments args) {
        if (ownerClerkUserId == null || ownerClerkUserId.isBlank()) {
            log.warn("OWNER_CLERK_USER_ID not set — no owner account will be able to authenticate");
            return;
        }
        if (userRepository.findByClerkUserId(ownerClerkUserId).isPresent()) {
            return;
        }

        User owner = new User("Owner", UserRole.OWNER);
        owner.setClerkUserId(ownerClerkUserId);
        userRepository.save(owner);
        log.info("Bootstrapped owner user for Clerk id {}", ownerClerkUserId);
    }
}
