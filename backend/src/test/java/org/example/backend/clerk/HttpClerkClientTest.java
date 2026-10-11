package org.example.backend.clerk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the request HttpClerkClient builds and how it reads the reply, against a local stub, not real Clerk. */
class HttpClerkClientTest {

    private HttpServer server;
    private String capturedPath;
    private String capturedAuthorization;
    private int stubStatus = 200;
    private String stubResponse = """
            {"id":"user_1","email_addresses":[
              {"email_address":"verified@example.com","verification":{"status":"verified"}},
              {"email_address":"unverified@example.com","verification":{"status":"unverified"}},
              {"email_address":"noverification@example.com","verification":null}]}
            """;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath = exchange.getRequestURI().toString();
            capturedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] response = stubResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stubStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpClerkClient client(String key) {
        return new HttpClerkClient(key, "http://localhost:" + server.getAddress().getPort());
    }

    @Test
    void asksForTheUserWithTheSecretKeyAndKeepsOnlyVerifiedEmails() {
        assertThat(client("sk_test_123").verifiedEmails("user_1")).containsExactly("verified@example.com");
        assertThat(capturedPath).isEqualTo("/v1/users/user_1");
        assertThat(capturedAuthorization).isEqualTo("Bearer sk_test_123");
    }

    @Test
    void returnsNothingWhenClerkAnswersWithAnError() {
        stubStatus = 404;
        stubResponse = "{\"errors\":[]}";
        assertThat(client("sk_test_123").verifiedEmails("user_1")).isEmpty();
    }

    @Test
    void returnsNothingAndDoesNotCallClerkWithoutASecretKey() {
        assertThat(client("").verifiedEmails("user_1")).isEmpty();
        assertThat(capturedPath).isNull();
    }
}
