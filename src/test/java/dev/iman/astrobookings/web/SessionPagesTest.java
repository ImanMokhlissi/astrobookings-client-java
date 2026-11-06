package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.session.SessionStore;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SessionPagesTest {

    private static final String AUTH_BODY =
            "{\"user\":{\"username\":\"ana\",\"email\":\"ana@example.com\"},\"accessToken\":\"token-1\"}";
    private static final String EMAIL_TAKEN_BODY = """
            {"message":"Validation failed",
             "errors":[{"field":"email","message":"Email is already registered"}]}
            """;
    private static final String UNAUTHORIZED_BODY = "{\"message\":\"Invalid credentials\",\"errors\":[]}";
    private static final String PASSWORD = "s3cret-password";

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
    void should_send_the_fields_with_terms_true_when_registering() {
        web.fakeApi().respondWith(201, AUTH_BODY);
        web.startServer();

        postRegistration("  ana ", " ana@example.com ");

        String body = web.fakeApi().receivedBodies().get(0);
        assertEquals("/users", web.fakeApi().receivedPath());
        assertTrue(body.contains("\"username\":\"ana\""));
        assertTrue(body.contains("\"email\":\"ana@example.com\""));
        assertTrue(body.contains("\"password\":\"" + PASSWORD + "\""));
        assertTrue(body.contains("\"terms\":true"));
    }

    @Test
    void should_save_the_session_without_the_password_and_redirect_with_303_when_registering() throws IOException {
        web.fakeApi().respondWith(201, AUTH_BODY);
        web.startServer();

        HttpResponse<String> response = postRegistration("ana", "ana@example.com");

        assertEquals(303, response.statusCode());
        assertEquals("/", response.headers().firstValue("Location").orElseThrow());
        assertEquals(Optional.of(WebTestFixture.ANA), new SessionStore(web.sessionFile()).read());
        assertFalse(Files.readString(web.sessionFile()).contains(PASSWORD));
    }

    @Test
    void should_show_who_is_logged_in_on_the_page_it_redirects_to_when_registering() {
        web.fakeApi().queueResponse(201, AUTH_BODY);
        web.startServer();

        HttpResponse<String> response = postRegistration("ana", "ana@example.com");

        assertTrue(web.followRedirect(response).body().contains("Logged in as ana (ana@example.com)"));
    }

    @Test
    void should_save_the_session_and_redirect_with_303_when_login_succeeds() {
        web.fakeApi().respondWith(200, AUTH_BODY);
        web.startServer();

        HttpResponse<String> response = web.post("/login", "email", " ana@example.com ", "password", PASSWORD);

        assertEquals(303, response.statusCode());
        assertEquals("/", response.headers().firstValue("Location").orElseThrow());
        assertEquals("/login", web.fakeApi().receivedPath());
        assertEquals(Optional.of(WebTestFixture.ANA), new SessionStore(web.sessionFile()).read());
    }

    @Test
    void should_show_who_is_logged_in_when_login_succeeds() {
        web.fakeApi().queueResponse(200, AUTH_BODY);
        web.startServer();

        HttpResponse<String> response = web.post("/login", "email", "ana@example.com", "password", PASSWORD);

        assertTrue(web.followRedirect(response).body().contains("Logged in as ana (ana@example.com)"));
    }

    @Test
    void should_show_the_form_without_a_request_when_the_terms_are_not_accepted() {
        web.startServer();

        HttpResponse<String> response = web.post(
                "/register", "username", "ana", "email", "ana@example.com", "password", PASSWORD);

        assertNotEquals(303, response.statusCode());
        assertEquals(0, web.fakeApi().receivedRequests());
        assertTrue(response.body().contains("The terms were not accepted, so you were not registered."));
        assertTrue(response.body().contains("value=\"ana\""));
        assertTrue(response.body().contains("value=\"ana@example.com\""));
        assertFalse(Files.exists(web.sessionFile()));
    }

    @Test
    void should_show_the_form_with_the_typed_values_and_an_empty_password_when_the_email_is_already_registered() {
        web.fakeApi().respondWith(400, EMAIL_TAKEN_BODY);
        web.startServer();

        HttpResponse<String> response = postRegistration("ana", "ana@example.com");

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("email: Email is already registered"));
        assertTrue(response.body().contains("value=\"ana\""));
        assertTrue(response.body().contains("value=\"ana@example.com\""));
        assertFalse(response.body().contains(PASSWORD));
    }

    @Test
    void should_start_no_session_when_the_email_is_already_registered() {
        web.fakeApi().respondWith(400, EMAIL_TAKEN_BODY);
        web.startServer();

        HttpResponse<String> response = postRegistration("ana", "ana@example.com");

        assertFalse(response.body().contains("Logged in as"));
        assertFalse(Files.exists(web.sessionFile()));
    }

    @Test
    void should_escape_the_typed_values_in_their_attributes_when_showing_the_form_again() {
        web.fakeApi().respondWith(400, EMAIL_TAKEN_BODY);
        web.startServer();

        String page = postRegistration("\"><script>", "'><img>").body();

        assertTrue(page.contains("value=\"&quot;&gt;&lt;script&gt;\""));
        assertTrue(page.contains("value=\"&#39;&gt;&lt;img&gt;\""));
        assertFalse(page.contains("<script"));
        assertFalse(page.contains("<img"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/register", "/login"})
    void should_never_echo_the_password_when_showing_a_form_again(String path) {
        web.fakeApi().respondWith(401, UNAUTHORIZED_BODY);
        web.startServer();

        String page = web.post(path, "username", "ana", "email", "a@b.c", "password", PASSWORD, "terms", "accepted")
                .body();

        assertFalse(page.contains(PASSWORD));
    }

    @Test
    void should_state_that_the_login_is_broken_when_opening_the_login_page() {
        web.startServer();

        String page = web.get("/login").body();

        assertTrue(page.contains("The login of the API is currently broken and rejects every attempt."));
        assertEquals(0, web.fakeApi().receivedRequests());
    }

    @Test
    void should_explain_and_link_to_registration_without_a_redirect_when_login_responds_401() {
        web.fakeApi().respondWith(401, UNAUTHORIZED_BODY);
        web.startServer();

        HttpResponse<String> response = web.post("/login", "email", "ana@example.com", "password", PASSWORD);

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("The API did not accept the email or password"));
        assertTrue(response.body().contains("its login is broken today"));
        assertTrue(response.body().contains("href=\"/register\">Register instead"));
        assertTrue(response.body().contains("value=\"ana@example.com\""));
    }

    @Test
    void should_keep_the_session_when_login_responds_401() {
        web.fakeApi().respondWith(401, UNAUTHORIZED_BODY);
        web.startServerWithSession();

        String page = web.post("/login", "email", "ana@example.com", "password", PASSWORD).body();

        assertTrue(page.contains("Logged in as ana"));
        assertTrue(Files.exists(web.sessionFile()));
    }

    @Test
    void should_delete_the_file_and_redirect_with_303_when_logging_out() {
        web.startServerWithSession();

        HttpResponse<String> response = web.post("/logout");

        assertEquals(303, response.statusCode());
        assertEquals("/", response.headers().firstValue("Location").orElseThrow());
        assertFalse(Files.exists(web.sessionFile()));
        assertFalse(web.followRedirect(response).body().contains("Logged in as"));
    }

    @Test
    void should_redirect_with_no_error_when_logging_out_with_no_file() {
        web.startServer();

        assertEquals(303, web.post("/logout").statusCode());
    }

    @Test
    void should_not_log_out_when_a_get_reaches_the_logout_path() {
        web.startServerWithSession();

        assertEquals(405, web.get("/logout").statusCode());

        assertTrue(Files.exists(web.sessionFile()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/register", "/login"})
    void should_show_an_error_with_no_stack_trace_when_the_session_cannot_be_saved(String path) throws IOException {
        Files.createDirectories(web.sessionFile());
        web.fakeApi().respondWith(200, AUTH_BODY);
        web.startServer();

        HttpResponse<String> response =
                web.post(path, "username", "ana", "email", "ana@example.com", "password", PASSWORD, "terms", "accepted");

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("The session could not be saved."));
        assertFalse(response.body().contains("Exception"));
    }

    @Test
    void should_show_an_error_with_no_stack_trace_when_the_session_file_cannot_be_deleted() throws IOException {
        Files.createDirectories(web.sessionFile().resolve("inside"));
        web.startServer();

        HttpResponse<String> response = web.post("/logout");

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("The session file could not be deleted."));
        assertFalse(response.body().contains("Exception"));
    }

    @Test
    void should_answer_a_page_with_no_stack_trace_when_the_form_body_is_malformed() {
        web.startServer();

        HttpResponse<String> response = web.postBody("/register", "username=%zz&terms=accepted");

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("The request could not be handled."));
        assertFalse(response.body().contains("Exception"));
        assertEquals(0, web.fakeApi().receivedRequests());
    }

    private HttpResponse<String> postRegistration(String username, String email) {
        return web.post(
                "/register", "username", username, "email", email, "password", PASSWORD, "terms", "accepted");
    }
}
