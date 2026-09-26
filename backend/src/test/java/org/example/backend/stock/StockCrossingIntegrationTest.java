package org.example.backend.stock;

import org.example.backend.notify.Notifier;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real Postgres configured via application.properties, like
 * every other stock integration test. Wrapped in @Transactional so nothing
 * written here is ever committed — which is also why StockCrossingListener
 * is a plain synchronous @EventListener rather than an AFTER_COMMIT one: an
 * AFTER_COMMIT listener would never fire under this rollback.
 *
 * Notifier calls leave no row in the DB to assert against (unlike everything
 * else in this app), so this test swaps in a small recording Notifier double
 * instead of reaching for a mocking framework this repo doesn't otherwise use.
 */
@SpringBootTest
@Transactional
class StockCrossingIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WriteOffService writeOffService;

    @Autowired
    private ReceiveStockService receiveStockService;

    @Autowired
    private RecordingNotifier notifier;

    @Test
    void firesOnceWhenBalanceCrossesBelowAlertLevelAndNotAgainWhileStillBelow() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product chicken = productRepository.save(new Product(
                "Crossing Test Chicken", ProductUnit.KG, new BigDecimal("45.00"), new BigDecimal("10.00")));
        receiveStockService.receive(chicken.getId(), null, new BigDecimal("20.00"), owner);
        notifier.messages.clear();

        writeOffService.writeOff(chicken.getId(), new BigDecimal("12.00"), "spoilage", owner);
        assertThat(notifier.messages).hasSize(1);
        assertThat(notifier.messages.get(0)).contains("Crossing Test Chicken");

        writeOffService.writeOff(chicken.getId(), new BigDecimal("1.00"), "spoilage", owner);
        assertThat(notifier.messages).hasSize(1);
    }

    @Test
    void doesNotFireWhileBalanceStaysAboveAlertLevel() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product oil = productRepository.save(new Product(
                "Crossing Test Oil", ProductUnit.UNIT, new BigDecimal("25.00"), new BigDecimal("5.00")));
        receiveStockService.receive(oil.getId(), null, new BigDecimal("20"), owner);
        notifier.messages.clear();

        writeOffService.writeOff(oil.getId(), new BigDecimal("2"), "damaged", owner);

        assertThat(notifier.messages).isEmpty();
    }

    @Test
    void receivingResetsSoALaterCrossingFiresAgain() {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        Product tinnedTomato = productRepository.save(new Product(
                "Crossing Test Tinned Tomato", ProductUnit.UNIT, new BigDecimal("8.00"), new BigDecimal("10.00")));
        receiveStockService.receive(tinnedTomato.getId(), null, new BigDecimal("20"), owner);
        notifier.messages.clear();

        writeOffService.writeOff(tinnedTomato.getId(), new BigDecimal("15"), "damaged", owner);
        assertThat(notifier.messages).hasSize(1);

        receiveStockService.receive(tinnedTomato.getId(), null, new BigDecimal("1"), owner);
        writeOffService.writeOff(tinnedTomato.getId(), new BigDecimal("1"), "damaged", owner);

        assertThat(notifier.messages).hasSize(2);
    }

    @TestConfiguration
    static class RecordingNotifierConfig {

        @Bean
        @Primary
        RecordingNotifier recordingNotifier() {
            return new RecordingNotifier();
        }
    }

    static class RecordingNotifier implements Notifier {

        final List<String> messages = new ArrayList<>();

        @Override
        public void send(String message) {
            messages.add(message);
        }
    }
}
