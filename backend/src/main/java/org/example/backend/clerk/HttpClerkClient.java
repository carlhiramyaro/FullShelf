package org.example.backend.clerk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls Clerk's Backend API (GET /v1/users/{id}) with the instance's secret
 * key. Same approach as ArkeselSmsGateway: RestClient built directly, no SDK
 * for one GET, explicit timeouts so a slow Clerk can't hang a login.
 *
 * Only an address whose verification status is "verified" is returned: the
 * JWT's subject proves who signed in, and this is what proves they own the
 * email the link is based on.
 */
@Component
public class HttpClerkClient implements ClerkClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClerkClient.class);

    private final RestClient restClient;
    private final String secretKey;

    public HttpClerkClient(@Value("${app.clerk-secret-key:}") String secretKey,
                           @Value("${app.clerk-api-base-url:https://api.clerk.com}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.secretKey = secretKey;
    }

    @Override
    public List<String> verifiedEmails(String clerkUserId) {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("CLERK_SECRET_KEY not set: cannot link owner {} to a shop", clerkUserId);
            return List.of();
        }
        try {
            Map<?, ?> user = restClient.get()
                    .uri("/v1/users/{id}", clerkUserId)
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve()
                    .body(Map.class);
            return verifiedEmailsIn(user);
        } catch (Exception e) {
            log.warn("Could not fetch Clerk user {}: {}", clerkUserId, e.toString());
            return List.of();
        }
    }

    private static List<String> verifiedEmailsIn(Map<?, ?> user) {
        List<String> emails = new ArrayList<>();
        if (user != null && user.get("email_addresses") instanceof List<?> addresses) {
            for (Object address : addresses) {
                if (address instanceof Map<?, ?> entry
                        && entry.get("verification") instanceof Map<?, ?> verification
                        && "verified".equals(verification.get("status"))
                        && entry.get("email_address") instanceof String email) {
                    emails.add(email);
                }
            }
        }
        return emails;
    }
}
