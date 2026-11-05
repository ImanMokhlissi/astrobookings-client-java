package dev.iman.astrobookings;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake API on a free local port, shared by the tests of every layer. Port 0 asks the system for
 * any free port. Stopping it releases a handler that is blocked, so a test never waits for it.
 */
public final class FakeApiServer {

    private record CannedResponse(int status, String body) {
    }

    private final CountDownLatch releaseBlockedHandlers = new CountDownLatch(1);
    private final AtomicInteger receivedRequests = new AtomicInteger();
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final Queue<CannedResponse> queuedResponses = new ConcurrentLinkedQueue<>();
    private final HttpServer server;
    private final String url;

    private volatile CannedResponse defaultResponse = new CannedResponse(200, "[]");
    private volatile boolean blocking = false;
    private volatile String receivedMethod;
    private volatile String receivedPath;
    private volatile String receivedRawPath;
    private volatile String receivedAuthorization;
    private volatile String receivedContentType;

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
        defaultResponse = new CannedResponse(status, body);
    }

    /** Queued responses answer the next requests in order, before the {@link #respondWith} one. */
    public void queueResponse(int status, String body) {
        queuedResponses.add(new CannedResponse(status, body));
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

    /** The path as it was sent, before any percent-decoding. */
    public String receivedRawPath() {
        return receivedRawPath;
    }

    public String receivedAuthorization() {
        return receivedAuthorization;
    }

    public String receivedContentType() {
        return receivedContentType;
    }

    /** The body of each request, in the order they arrived. */
    public List<String> receivedBodies() {
        return List.copyOf(receivedBodies);
    }

    private void handle(HttpExchange exchange) throws IOException {
        recordRequest(exchange);
        if (blocking) {
            waitForRelease();
            exchange.close();
            return;
        }
        CannedResponse queued = queuedResponses.poll();
        respond(exchange, queued != null ? queued : defaultResponse);
    }

    private static void respond(HttpExchange exchange, CannedResponse response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(response.status(), -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private void recordRequest(HttpExchange exchange) throws IOException {
        receivedMethod = exchange.getRequestMethod();
        receivedPath = exchange.getRequestURI().getPath();
        receivedRawPath = exchange.getRequestURI().getRawPath();
        receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        receivedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
        receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
