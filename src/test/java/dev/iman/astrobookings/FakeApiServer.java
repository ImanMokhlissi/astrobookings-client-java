package dev.iman.astrobookings;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake API on a free local port, shared by the tests of every layer. Port 0 asks the system for
 * any free port. Stopping it releases a handler that is blocked, so a test never waits for it.
 */
public final class FakeApiServer {

    private final CountDownLatch releaseBlockedHandlers = new CountDownLatch(1);
    private final AtomicInteger receivedRequests = new AtomicInteger();
    private final HttpServer server;
    private final String url;

    private volatile int responseStatus = 200;
    private volatile String responseBody = "[]";
    private volatile boolean blocking = false;
    private volatile String receivedMethod;
    private volatile String receivedPath;
    private volatile String receivedAuthorization;

    private FakeApiServer(HttpServer server, String url) {
        this.server = server;
        this.url = url;
    }

    public static FakeApiServer start() throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        String url = "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort();
        FakeApiServer fakeApi = new FakeApiServer(server, url);
        server.createContext("/", fakeApi::handle);
        server.start();
        return fakeApi;
    }

    /** One activity with every field, for the tests that only care about its name and status. */
    public static String activityJson(String name, String status) {
        return """
                {"id":"id-%1$s","name":"%1$s","slug":"slug-%1$s","price":158,"date":"2026-12-20",\
                "duration":60,"location":"Base","minParticipants":2,"maxParticipants":8,"status":"%2$s"}\
                """.formatted(name, status);
    }

    public void respondWith(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    /** The next requests get no response until the server stops. */
    public void blockResponses() {
        blocking = true;
    }

    public void stop() {
        releaseBlockedHandlers.countDown();
        server.stop(0);
    }

    public String url() {
        return url;
    }

    public int receivedRequests() {
        return receivedRequests.get();
    }

    public String receivedMethod() {
        return receivedMethod;
    }

    public String receivedPath() {
        return receivedPath;
    }

    public String receivedAuthorization() {
        return receivedAuthorization;
    }

    private void handle(HttpExchange exchange) throws IOException {
        recordRequest(exchange);
        if (blocking) {
            waitForRelease();
            exchange.close();
            return;
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private void recordRequest(HttpExchange exchange) {
        receivedMethod = exchange.getRequestMethod();
        receivedPath = exchange.getRequestURI().getPath();
        receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        receivedRequests.incrementAndGet();
    }

    private void waitForRelease() {
        try {
            releaseBlockedHandlers.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
