package org.example.backend.notify;

/**
 * The transport boundary between "what to send and to whom" (SmsNotifier)
 * and "how to actually call the SMS provider's HTTP API" (ArkeselSmsGateway).
 * Kept separate so SmsNotifier's own logic (resolving the owner's number,
 * never letting a send failure escape) can be tested with a hand-rolled fake
 * here, the same way Notifier itself is tested with a fake in
 * StockCrossingIntegrationTest, instead of reaching for a mocking framework.
 */
public interface SmsGateway {

    void send(String toE164, String body);
}
