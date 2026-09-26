package org.example.backend.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only Notifier bean until the SMS delivery slice adds a real one. Exists
 * so crossing-detection logic has somewhere to call today without waiting on
 * an SMS provider integration.
 */
@Component
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void send(String message) {
        log.warn("ALERT (no SMS provider configured yet): {}", message);
    }
}
