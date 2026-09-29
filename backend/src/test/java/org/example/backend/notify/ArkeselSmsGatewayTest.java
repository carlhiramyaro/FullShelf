package org.example.backend.notify;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the request ArkeselSmsGateway builds (auth header, path, JSON
 * body) against a local stub server, not real Arkesel — no automated test in
 * this app hits a real third-party API with billable side effects. Uses the
 * JDK's built-in HttpServer rather than adding a stubbing library dependency
 * for one test — same pattern the Twilio gateway's own test used.
 */
class ArkeselSmsGatewayTest {

    private HttpServer server;
    private String capturedPath;
    private String capturedApiKeyHeader;
    private String capturedBody;
    private String stubResponse = "{\"status\":\"success\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath = exchange.getRequestURI().toString();
            capturedApiKeyHeader = exchange.getRequestHeaders().getFirst("api-key");
            capturedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            byte[] response = stubResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
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
    void sendsAuthenticatedJsonRequestToArkeselsSendEndpointWithoutLeadingPlus() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ArkeselSmsGateway gateway = new ArkeselSmsGateway("testapikey", "FullShelf", true, baseUrl);

        gateway.send("+233241234567", "Chicken is at 5.00 KG (alert level 10.00)");

        assertThat(capturedPath).isEqualTo("/api/v2/sms/send");
        assertThat(capturedApiKeyHeader).isEqualTo("testapikey");
        assertThat(capturedBody).contains("\"sender\":\"FullShelf\"");
        assertThat(capturedBody).contains("\"message\":\"Chicken is at 5.00 KG (alert level 10.00)\"");
        assertThat(capturedBody).contains("\"recipients\":[\"233241234567\"]");
        assertThat(capturedBody).contains("\"sandbox\":true");
    }

    @Test
    void throwsWhenArkeselRespondsWithA200ButAnErrorStatus() {
        stubResponse = "{\"status\":\"error\",\"message\":\"sender id not approved\"}";
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ArkeselSmsGateway gateway = new ArkeselSmsGateway("testapikey", "FullShelf", false, baseUrl);

        assertThatThrownBy(() -> gateway.send("+233241234567", "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sender id not approved");
    }
}
