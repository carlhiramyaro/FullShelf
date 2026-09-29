package org.example.backend.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls Arkesel's SMS v2 API directly over RestClient (already on the
 * classpath transitively via spring-boot-starter-webmvc) rather than adding
 * a provider SDK for one POST request — same reasoning as the Twilio
 * gateway this replaced.
 *
 * Arkesel returns HTTP 200 even for some rejected sends, with the real
 * outcome in the response body's "status" field — checked explicitly so a
 * silently-rejected message can't be logged by SmsNotifier as sent.
 *
 * Explicit connect/read timeouts (SimpleClientHttpRequestFactory, no new
 * dependency — same "no SDK for one HTTP call" reasoning as the RestClient
 * choice itself) are set here rather than left at the JDK's default of no
 * timeout at all. SmsNotifier calls this synchronously inside the same
 * request/transaction as the write-off, sale or receive that triggered the
 * alert (see SmsNotifier's own decision on why a failure here must never
 * roll that back) — an unreachable or slow Arkesel must fail in seconds,
 * not hang that unrelated request until the OS's own TCP timeout.
 */
@Component
public class ArkeselSmsGateway implements SmsGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String apiKey;
    private final String senderId;
    private final boolean sandbox;

    public ArkeselSmsGateway(@Value("${app.arkesel-api-key:}") String apiKey,
                              @Value("${app.arkesel-sender-id:}") String senderId,
                              @Value("${app.arkesel-sandbox:false}") boolean sandbox,
                              @Value("${app.arkesel-base-url:https://sms.arkesel.com}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.senderId = senderId;
        this.sandbox = sandbox;
    }

    @Override
    public void send(String toE164, String body) {
        // Arkesel expects international numbers without the leading '+';
        // the rest of this app stores/passes numbers in E.164 (with '+'),
        // so the stripping stays local to this one provider quirk.
        String recipient = toE164.startsWith("+") ? toE164.substring(1) : toE164;

        Map<String, Object> requestBody = Map.of(
                "sender", senderId,
                "message", body,
                "recipients", List.of(recipient),
                "sandbox", sandbox);

        Map<?, ?> response = restClient.post()
                .uri("/api/v2/sms/send")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        Object status = response == null ? null : response.get("status");
        if (!"success".equals(status)) {
            throw new IllegalStateException("Arkesel rejected the message: " + response);
        }
    }
}
