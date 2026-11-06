package dev.iman.astrobookings.web;

import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchService;
import dev.iman.astrobookings.service.SessionService;
import dev.iman.astrobookings.session.SessionStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The layers as {@code Main} builds them, with the fake API, a temporary session file and port 0. The
 * server reads the session when it starts, so a test saves one before calling {@link #startServer()}.
 * The HTTP client does not follow redirects, so a test can check the 303 and its {@code Location}.
 */
final class WebTestFixture {

    static final Session ANA = new Session("token-1", "ana", "ana@example.com");

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient browser = HttpClient.newHttpClient();
    private final FakeApiServer fakeApi;
    private final Path sessionFile;
    private final SessionStore store;
    private WebServer server;

    private WebTestFixture(FakeApiServer fakeApi, Path sessionFile) {
        this.fakeApi = fakeApi;
        this.sessionFile = sessionFile;
        this.store = new SessionStore(sessionFile);
    }

    static WebTestFixture over(Path folder) {
        try {
            return new WebTestFixture(FakeApiServer.start(), folder.resolve("session.json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void startServer() {
        startServerWithTimeout(REQUEST_TIMEOUT);
    }

    void startServerWithSession() {
        saveSession(ANA);
        startServer();
    }

    void startServerWithTimeout(Duration apiTimeout) {
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), apiTimeout);
        LaunchService launchService = new LaunchService(apiClient);
        SessionService sessionService = new SessionService(apiClient, store);
        BookingService bookingService = new BookingService(apiClient, launchService, sessionService);
        server = WebServer.start(launchService, sessionService, bookingService, 0);
    }

    void saveSession(Session session) {
        store.write(session);
    }

    /** Stops the server first, so that nothing is waiting on the fake API when it stops. */
    void stop() {
        if (server != null) {
            server.stop();
        }
        fakeApi.stop();
    }

    FakeApiServer fakeApi() {
        return fakeApi;
    }

    Path sessionFile() {
        return sessionFile;
    }

    int port() {
        return server.port();
    }

    HttpResponse<String> get(String path) {
        return send(requestTo(path).GET().build());
    }

    /** The fields go as name, value, name, value, and so on. */
    HttpResponse<String> post(String path, String... fields) {
        return postBody(path, encode(fields));
    }

    HttpResponse<String> postBody(String path, String body) {
        return send(formRequestTo(path, body).build());
    }

    HttpResponse<String> postWithOrigin(String path, String origin, String... fields) {
        return send(formRequestTo(path, encode(fields)).header("Origin", origin).build());
    }

    HttpResponse<String> followRedirect(HttpResponse<String> redirect) {
        return get(redirect.headers().firstValue("Location").orElseThrow());
    }

    private HttpRequest.Builder formRequestTo(String path, String body) {
        return requestTo(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder requestTo(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port() + path)).timeout(REQUEST_TIMEOUT);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return browser.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String... fields) {
        List<String> pairs = new ArrayList<>();
        for (int index = 0; index < fields.length; index += 2) {
            pairs.add(encodeOne(fields[index]) + "=" + encodeOne(fields[index + 1]));
        }
        return String.join("&", pairs);
    }

    private static String encodeOne(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
