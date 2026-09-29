package org.example.backend.notify;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The real Notifier bean, active only where app.sms.enabled=true and real
 * Arkesel credentials are configured (Railway) — LoggingNotifier stays the
 * default everywhere else so local dev never fires a real SMS while testing
 * other slices.
 *
 * Resolves the owner's number fresh on every send rather than caching it,
 * since she can change it from /owner/settings at any time.
 *
 * Never lets a gateway failure propagate: StockCrossingListener calls
 * Notifier.send(...) synchronously inside the same transaction as the stock
 * movement that triggered it (see that class's own decision on why it isn't
 * an AFTER_COMMIT listener), so an Arkesel outage or bad request must not
 * roll back a real sale/receive/write-off. A missed alert is an acceptable
 * best-effort gap; a lost ledger write is not.
 */
@Component
@ConditionalOnProperty(name = "app.sms.enabled", havingValue = "true")
public class SmsNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SmsNotifier.class);

    private final UserRepository userRepository;
    private final SmsGateway smsGateway;

    public SmsNotifier(UserRepository userRepository, SmsGateway smsGateway) {
        this.userRepository = userRepository;
        this.smsGateway = smsGateway;
    }

    @Override
    public void send(String message) {
        List<User> owners = userRepository.findByRoleOrderByName(UserRole.OWNER);
        if (owners.isEmpty()) {
            log.warn("No owner account exists yet — dropping alert: {}", message);
            return;
        }

        String phoneNumber = owners.get(0).getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("Owner has no phone number set (see /owner/settings) — dropping alert: {}", message);
            return;
        }

        try {
            smsGateway.send(phoneNumber, message);
            log.info("Sent SMS alert to {}: {}", phoneNumber, message);
        } catch (Exception ex) {
            log.error("Failed to send SMS alert to {}: {}", phoneNumber, message, ex);
        }
    }
}
