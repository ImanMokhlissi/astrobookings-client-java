package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A fake API on a free local port. Port 0 asks the system for any free port, so tests never
 * collide. In a real test, pass {@code baseUrl} to the API client instead of calling HttpClient.
 */
class FakeApiServerTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();

    private HttpServer server;
    private URI baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.start();
        baseUrl = URI.create("http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void should_return_the_body_when_the_server_responds_ok() throws Exception {
        // Arrange
        respondWith("/items", 200, "[]");

        // Act
        HttpResponse<String> response = get("/items");

        // Assert
        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }

    @Test
    void should_receive_the_error_when_the_server_rejects_the_request() throws Exception {
        // Arrange
        respondWith("/items", 400, "{\"message\":\"Invalid\",\"errors\":[]}");

        // Act
        HttpResponse<String> response = get("/items");

        // Assert
        assertEquals(400, response.statusCode());
        assertEquals("{\"message\":\"Invalid\",\"errors\":[]}", response.body());
    }

    @Test
    void should_capture_the_authorization_header_when_the_request_sends_it() throws Exception {
        // Arrange
        respondWith("/items", 200, "[]");
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/items"))
                .header("Authorization", "Bearer token")
                .build();

        // Act
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        assertEquals("Bearer token", receivedAuthorization.get());
    }

    @Test
    void should_fail_to_connect_when_the_server_is_stopped() {
        // Arrange
        server.stop(0);

        // Act and assert
        assertThrows(IOException.class, () -> get("/items"));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve(path)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void respondWith(String path, int status, String body) {
        server.createContext(path, exchange -> {
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
    }
}
