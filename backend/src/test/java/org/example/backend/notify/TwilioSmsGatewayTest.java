package org.example.backend.notify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the request TwilioSmsGateway builds (auth header, path, form
 * body) against a local stub server, not real Twilio — no automated test in
 * this app hits a real third-party API with billable side effects. Uses the
 * JDK's built-in HttpServer rather than adding a stubbing library dependency
 * for one test.
 */
class TwilioSmsGatewayTest {

    private HttpServer server;
    private String capturedPath;
    private String capturedAuthHeader;
    private String capturedBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath = exchange.getRequestURI().toString();
            capturedAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            capturedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            byte[] response = "{\"sid\":\"SMtest\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsAuthenticatedFormEncodedRequestToTwiliosMessagesEndpoint() throws IOException {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        TwilioSmsGateway gateway = new TwilioSmsGateway(
                "ACtestsid", "authtoken123", "+15005550006", baseUrl);

        gateway.send("+233241234567", "Chicken is at 5.00 KG (alert level 10.00)");

        assertThat(capturedPath).isEqualTo("/2010-04-01/Accounts/ACtestsid/Messages.json");

        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("ACtestsid:authtoken123".getBytes(StandardCharsets.UTF_8));
        assertThat(capturedAuthHeader).isEqualTo(expectedAuth);

        Map<String, String> form = parseForm(capturedBody);
        assertThat(form.get("To")).isEqualTo("+233241234567");
        assertThat(form.get("From")).isEqualTo("+15005550006");
        assertThat(form.get("Body")).isEqualTo("Chicken is at 5.00 KG (alert level 10.00)");
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
