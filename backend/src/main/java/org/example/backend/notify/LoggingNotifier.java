package org.example.backend.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default Notifier bean everywhere app.sms.enabled isn't explicitly
 * "true" (i.e. local dev, and any environment without real Twilio
 * credentials configured yet) — see SmsNotifier for the real one.
 */
@Component
@ConditionalOnProperty(name = "app.sms.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void send(String message) {
        log.warn("ALERT (no SMS provider configured yet): {}", message);
    }
}
