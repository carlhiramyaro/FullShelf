package org.example.backend.notify;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * app.sms.enabled=true here so the real SmsNotifier bean (rather than
 * LoggingNotifier) is wired into this test's context. The gateway itself is
 * swapped for a hand-rolled recording fake via @TestConfiguration/@Primary —
 * same pattern StockCrossingIntegrationTest uses for Notifier — so no test
 * here ever calls out to real Arkesel.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.sms.enabled=true")
class SmsNotifierIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SmsNotifier smsNotifier;

    @Autowired
    private RecordingSmsGateway smsGateway;

    @BeforeEach
    void resetGateway() {
        smsGateway.sent.clear();
        smsGateway.failNext = false;
    }

    @Test
    void sendsToTheOwnersConfiguredPhoneNumber() {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setPhoneNumber("+233241234567");
        userRepository.save(owner);

        smsNotifier.send("Chicken is at 5.00 KG (alert level 10.00)");

        assertThat(smsGateway.sent).hasSize(1);
        assertThat(smsGateway.sent.get(0).to()).isEqualTo("+233241234567");
        assertThat(smsGateway.sent.get(0).body()).isEqualTo("Chicken is at 5.00 KG (alert level 10.00)");
    }

    @Test
    void ignoresADeactivatedOwnerEvenIfItSortsFirstAndHasAPhoneNumber() {
        User stale = new User("Aaa stale owner", UserRole.OWNER);
        stale.setPhoneNumber("+233200000000");
        stale.setActive(false);
        userRepository.save(stale);
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setPhoneNumber("+233241234567");
        userRepository.save(owner);

        smsNotifier.send("Chicken is at 5.00 KG (alert level 10.00)");

        assertThat(smsGateway.sent).hasSize(1);
        assertThat(smsGateway.sent.get(0).to()).isEqualTo("+233241234567");
    }

    @Test
    void doesNothingWhenOwnerHasNoPhoneNumberSetYet() {
        userRepository.save(new User("Aunt Amerley", UserRole.OWNER));

        smsNotifier.send("Chicken is at 5.00 KG (alert level 10.00)");

        assertThat(smsGateway.sent).isEmpty();
    }

    @Test
    void swallowsAGatewayFailureRatherThanPropagatingIt() {
        User owner = new User("Aunt Amerley", UserRole.OWNER);
        owner.setPhoneNumber("+233241234567");
        userRepository.save(owner);
        smsGateway.failNext = true;

        assertThatCode(() -> smsNotifier.send("Chicken is at 5.00 KG (alert level 10.00)"))
                .doesNotThrowAnyException();
    }

    @TestConfiguration
    static class RecordingSmsGatewayConfig {

        @Bean
        @Primary
        RecordingSmsGateway recordingSmsGateway() {
            return new RecordingSmsGateway();
        }
    }

    static class RecordingSmsGateway implements SmsGateway {

        record Sent(String to, String body) {
        }

        final List<Sent> sent = new ArrayList<>();
        boolean failNext = false;

        @Override
        public void send(String toE164, String body) {
            if (failNext) {
                failNext = false;
                throw new RuntimeException("simulated gateway failure");
            }
            sent.add(new Sent(toE164, body));
        }
    }
}
