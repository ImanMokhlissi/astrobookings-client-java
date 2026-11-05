package dev.iman.astrobookings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.InvalidCredentialsFailure;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.SessionFileFailure;
import dev.iman.astrobookings.model.SessionRejectedFailure;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import dev.iman.astrobookings.session.SessionStore;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionServiceTest {

    private static final Session ANA = new Session("token-1", "ana", "ana@example.com");
    private static final Session BEA = new Session("token-2", "bea", "bea@example.com");
    private static final String ANA_BODY = """
            {"user":{"username":"ana","email":"ana@example.com"},"accessToken":"token-1"}
            """;
    private static final String BEA_BODY = """
            {"user":{"username":"bea","email":"bea@example.com"},"accessToken":"token-2"}
            """;
    private static final String UNAUTHORIZED_BODY = "{\"message\":\"Authentication failed\",\"errors\":[]}";
    private static final UnauthorizedFailure UNAUTHORIZED =
            new UnauthorizedFailure(new ApiError(401, "Unauthorized", List.of()));

    @TempDir
    Path folder;

    private FakeApiServer fakeApi;
    private ApiClient apiClient;
    private Path file;
    private SessionStore store;
    private SessionService service;

    @BeforeEach
    void setUp() throws IOException {
        fakeApi = FakeApiServer.start();
        apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), Duration.ofSeconds(10));
        file = folder.resolve("session.json");
        store = new SessionStore(file);
        service = new SessionService(apiClient, store);
    }

    @AfterEach
    void stopServer() {
        fakeApi.stop();
    }

    @Test
    void should_save_and_start_the_session_when_registering_succeeds() {
        fakeApi.respondWith(201, ANA_BODY);

        Session session = service.register("ana", "ana@example.com", "secret");

        assertEquals(ANA, session);
        assertEquals(Optional.of(ANA), service.currentSession());
        assertEquals(Optional.of(ANA), store.read());
    }

    @Test
    void should_save_and_start_the_session_when_logging_in_succeeds() {
        fakeApi.respondWith(200, ANA_BODY);

        Session session = service.login("ana@example.com", "secret");

        assertEquals(ANA, session);
        assertEquals(Optional.of(ANA), service.currentSession());
        assertEquals(Optional.of(ANA), store.read());
    }

    @Test
    void should_replace_the_session_when_logging_in_again() {
        fakeApi.queueResponse(200, ANA_BODY);
        fakeApi.queueResponse(200, BEA_BODY);
        service.login("ana@example.com", "secret");

        service.login("bea@example.com", "secret");

        assertEquals(Optional.of(BEA), service.currentSession());
        assertEquals(Optional.of(BEA), store.read());
    }

    @Test
    void should_start_the_session_without_calling_the_api_when_loading_a_saved_file() {
        store.write(ANA);

        service.loadSavedSession();

        assertEquals(Optional.of(ANA), service.currentSession());
        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_have_no_session_when_there_is_no_file() {
        service.loadSavedSession();

        assertEquals(Optional.empty(), service.currentSession());
    }

    @Test
    void should_have_no_session_and_keep_the_file_when_it_cannot_be_read() throws IOException {
        Files.writeString(file, "not json");

        assertThrows(SessionFileFailure.class, service::loadSavedSession);

        assertEquals(Optional.empty(), service.currentSession());
        assertEquals("not json", Files.readString(file));
    }

    @Test
    void should_fail_as_invalid_credentials_when_login_responds_401() {
        fakeApi.respondWith(401, UNAUTHORIZED_BODY);

        assertThrows(InvalidCredentialsFailure.class, () -> service.login("ana@example.com", "wrong"));
    }

    @Test
    void should_keep_the_file_and_session_when_login_responds_401() {
        startWithSavedSession(ANA);
        fakeApi.respondWith(401, UNAUTHORIZED_BODY);

        assertThrows(InvalidCredentialsFailure.class, () -> service.login("bea@example.com", "wrong"));

        assertEquals(Optional.of(ANA), service.currentSession());
        assertEquals(Optional.of(ANA), store.read());
    }

    @Test
    void should_keep_the_file_and_session_when_registration_responds_401() {
        startWithSavedSession(ANA);
        fakeApi.respondWith(401, UNAUTHORIZED_BODY);

        assertThrows(UnauthorizedFailure.class, () -> service.register("bea", "bea@example.com", "secret"));

        assertEquals(Optional.of(ANA), service.currentSession());
        assertEquals(Optional.of(ANA), store.read());
    }

    @Test
    void should_have_no_session_when_saving_fails() throws IOException {
        Path regularFile = Files.writeString(folder.resolve("regular-file"), "text");
        SessionService serviceUnderFile =
                new SessionService(apiClient, new SessionStore(regularFile.resolve("session.json")));
        fakeApi.respondWith(201, ANA_BODY);

        assertThrows(SessionFileFailure.class, () -> serviceUnderFile.register("ana", "ana@example.com", "secret"));

        assertEquals(Optional.empty(), serviceUnderFile.currentSession());
    }

    @Test
    void should_delete_the_file_and_end_the_session_when_logging_out() {
        startWithSavedSession(ANA);

        service.logout();

        assertFalse(Files.exists(file));
        assertEquals(Optional.empty(), service.currentSession());
    }

    @Test
    void should_not_fail_when_logging_out_without_a_file() {
        service.logout();

        assertEquals(Optional.empty(), service.currentSession());
    }

    @Test
    void should_delete_the_file_and_end_the_session_when_an_authenticated_call_responds_401() {
        startWithSavedSession(ANA);

        assertThrows(SessionRejectedFailure.class, this::runRejectedCall);

        assertFalse(Files.exists(file));
        assertEquals(Optional.empty(), service.currentSession());
    }

    @Test
    void should_fail_as_session_rejected_when_an_authenticated_call_responds_401() {
        startWithSavedSession(ANA);

        SessionRejectedFailure failure = assertThrows(SessionRejectedFailure.class, this::runRejectedCall);

        assertInstanceOf(UnauthorizedFailure.class, failure.getCause());
    }

    @Test
    void should_fail_as_session_rejected_when_the_file_cannot_be_deleted() throws IOException {
        Files.createDirectory(file);
        Files.writeString(file.resolve("inside"), "text");

        assertThrows(SessionRejectedFailure.class, this::runRejectedCall);

        assertEquals(Optional.empty(), service.currentSession());
    }

    @Test
    void should_keep_the_session_when_an_authenticated_call_fails_otherwise() {
        startWithSavedSession(ANA);
        ApiErrorFailure otherFailure = new ApiErrorFailure(new ApiError(500, "Broken", List.of()));

        ApiErrorFailure failure = assertThrows(ApiErrorFailure.class, () -> service.runAuthenticated(() -> {
            throw otherFailure;
        }));

        assertEquals(otherFailure, failure);
        assertEquals(Optional.of(ANA), service.currentSession());
        assertTrue(Files.exists(file));
    }

    @Test
    void should_return_the_result_when_an_authenticated_call_succeeds() {
        startWithSavedSession(ANA);

        String result = service.runAuthenticated(() -> "result");

        assertEquals("result", result);
        assertEquals(Optional.of(ANA), service.currentSession());
    }

    private void startWithSavedSession(Session session) {
        store.write(session);
        service.loadSavedSession();
    }

    private void runRejectedCall() {
        service.runAuthenticated(() -> {
            throw UNAUTHORIZED;
        });
    }
}
