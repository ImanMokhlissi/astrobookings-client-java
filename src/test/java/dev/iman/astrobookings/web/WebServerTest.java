package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchService;
import dev.iman.astrobookings.service.SessionService;
import dev.iman.astrobookings.session.SessionStore;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebServerTest {

    @TempDir
    Path folder;

    private WebTestFixture web;

    @BeforeEach
    void setUp() {
        web = WebTestFixture.over(folder);
    }

    @AfterEach
    void tearDown() {
        web.stop();
    }

    @Test
    void should_listen_only_on_127_0_0_1_when_started() {
        web.startServer();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest requestToAnotherAddress = HttpRequest.newBuilder(URI.create("http://127.0.0.2:" + web.port() + "/"))
                .timeout(Duration.ofSeconds(10))
                .build();

        assertEquals(200, web.get("/").statusCode());
        assertThrows(
                IOException.class, () -> client.send(requestToAnotherAddress, HttpResponse.BodyHandlers.discarding()));
    }

    @Test
    void should_report_the_bound_port_when_started_on_port_0() {
        web.startServer();

        assertNotEquals(0, web.port());
        assertEquals(200, web.get("/style.css").statusCode());
    }

    @Test
    void should_fail_with_a_start_failure_when_the_port_is_busy() throws IOException {
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), web.fakeApi().url(), Duration.ofSeconds(1));
        LaunchService launchService = new LaunchService(apiClient);
        SessionService sessionService = new SessionService(apiClient, new SessionStore(web.sessionFile()));
        BookingService bookingService = new BookingService(apiClient, launchService, sessionService);
        InetAddress address = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});

        try (ServerSocket holder = new ServerSocket(0, 0, address)) {
            int busyPort = holder.getLocalPort();
            assertThrows(
                    WebServerStartFailure.class,
                    () -> WebServer.start(launchService, sessionService, bookingService, busyPort));
        }
    }

    @Test
    void should_answer_404_when_the_path_is_unknown() {
        web.startServer();

        assertEquals(404, web.get("/launchpad").statusCode());
    }

    @Test
    void should_answer_405_when_the_method_does_not_match() {
        web.startServer();

        assertEquals(405, web.post("/bookings").statusCode());
        assertEquals(405, web.post("/").statusCode());
        assertEquals(405, web.get("/logout").statusCode());
    }

    @Test
    void should_serve_the_css_when_requested() {
        web.startServer();

        HttpResponse<String> response = web.get("/style.css");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/css"));
        assertTrue(response.body().contains("font-family"));
    }

    @Test
    void should_show_a_notice_when_the_session_file_cannot_be_read() throws IOException {
        Files.writeString(web.sessionFile(), "this is not a session");
        web.startServer();

        HttpResponse<String> response = web.get("/launchpad");

        assertTrue(response.body().contains("The session file could not be read. Continuing with no session."));
        assertFalse(response.body().contains("Logged in as"));
    }

    @Test
    void should_stop_showing_the_notice_when_logging_out() throws IOException {
        Files.writeString(web.sessionFile(), "this is not a session");
        web.startServer();

        web.post("/logout");

        assertFalse(web.get("/launchpad").body().contains("The session file could not be read"));
    }

    @Test
    void should_stop_showing_the_notice_when_a_session_was_saved_and_then_rejected() throws IOException {
        Files.writeString(web.sessionFile(), "this is not a session");
        web.startServer();
        Files.delete(web.sessionFile());
        web.fakeApi().queueResponse(201, "{\"user\":{\"username\":\"ana\",\"email\":\"a@b.c\"},\"accessToken\":\"t\"}");
        web.fakeApi().queueResponse(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");

        web.post("/register", "username", "ana", "email", "a@b.c", "password", "p", "terms", "accepted");
        String page = web.get("/bookings").body();

        assertTrue(page.contains("Your session is no longer valid."));
        assertFalse(page.contains("The session file could not be read"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://evil.example", "http://127.0.0.1:1", "null", "", "https://127.0.0.1:PORT"})
    void should_answer_403_without_calling_the_api_when_a_post_has_a_foreign_origin(String origin) {
        web.startServerWithSession();

        HttpResponse<String> response = web.postWithOrigin("/book", withPort(origin), "launchId", "id-1", "seats", "1");

        assertEquals(403, response.statusCode());
        assertEquals(0, web.fakeApi().receivedRequests());
    }

    @Test
    void should_keep_the_session_when_the_origin_is_foreign() {
        web.startServerWithSession();

        HttpResponse<String> response = web.postWithOrigin("/logout", "http://evil.example");

        assertEquals(403, response.statusCode());
        assertTrue(Files.exists(web.sessionFile()));
        assertTrue(web.get("/launchpad").body().contains("Logged in as ana"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:PORT", "http://localhost:PORT"})
    void should_accept_a_post_when_the_origin_is_the_server_itself(String origin) {
        web.startServerWithSession();

        HttpResponse<String> response = web.postWithOrigin("/logout", withPort(origin));

        assertEquals(303, response.statusCode());
    }

    @Test
    void should_accept_a_post_when_it_has_no_origin() {
        web.startServerWithSession();

        assertEquals(303, web.post("/logout").statusCode());
    }

    private String withPort(String origin) {
        return origin.replace("PORT", String.valueOf(web.port()));
    }
}
