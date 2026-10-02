package org.example.backend.security;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions are phrased as "exactly one active owner, with the configured
 * id" rather than naming specific rows, because the local dev Postgres may
 * already hold a real owner row from running the app by hand — these tests
 * must hold whatever is there, and the surrounding @Transactional rolls
 * everything back.
 */
@SpringBootTest
@Transactional
class OwnerBootstrapRunnerIntegrationTest {

    private static final String NEW_ID = "user_bootstrap_test_new";

    @Autowired
    private UserRepository userRepository;

    private void runBootstrap() {
        new OwnerBootstrapRunner(userRepository, NEW_ID).run(null);
    }

    private List<User> activeOwners() {
        return userRepository.findByRoleAndActiveTrueOrderByIdAsc(UserRole.OWNER);
    }

    @Test
    void deactivatesOtherOwnersWhenTheConfiguredOneAlreadyExists() {
        User configured = new User("Owner", UserRole.OWNER);
        configured.setClerkUserId(NEW_ID);
        userRepository.save(configured);
        User stale = new User("Owner", UserRole.OWNER);
        stale.setClerkUserId("user_bootstrap_test_stale");
        userRepository.save(stale);

        runBootstrap();

        assertThat(activeOwners()).extracting(User::getId).containsExactly(configured.getId());
        assertThat(userRepository.findById(stale.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void relinksTheExistingOwnerInsteadOfInsertingASecondOneAndClearsItsPhone() {
        User previous = new User("Owner", UserRole.OWNER);
        previous.setClerkUserId("user_bootstrap_test_previous");
        previous.setPhoneNumber("+233241234567");
        userRepository.save(previous);
        long ownersBefore = userRepository.findByRoleOrderByName(UserRole.OWNER).size();

        runBootstrap();

        assertThat(userRepository.findByRoleOrderByName(UserRole.OWNER)).hasSize((int) ownersBefore);
        List<User> active = activeOwners();
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getClerkUserId()).isEqualTo(NEW_ID);
        assertThat(active.get(0).getPhoneNumber()).isNull();
    }

    @Test
    void createsAnOwnerWhenNoActiveOwnerExists() {
        userRepository.findByRoleOrderByName(UserRole.OWNER).forEach(u -> u.setActive(false));
        userRepository.flush();

        runBootstrap();

        List<User> active = activeOwners();
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getClerkUserId()).isEqualTo(NEW_ID);
    }

    @Test
    void runningTwiceChangesNothingTheSecondTime() {
        runBootstrap();
        long ownersAfterFirst = userRepository.findByRoleOrderByName(UserRole.OWNER).size();
        Long activeIdAfterFirst = activeOwners().get(0).getId();

        runBootstrap();

        assertThat(userRepository.findByRoleOrderByName(UserRole.OWNER)).hasSize((int) ownersAfterFirst);
        assertThat(activeOwners()).extracting(User::getId).containsExactly(activeIdAfterFirst);
    }
}
