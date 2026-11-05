package dev.iman.astrobookings.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.LaunchNotBookableFailure;
import dev.iman.astrobookings.model.NoSessionFailure;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.SessionRejectedFailure;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchService;
import dev.iman.astrobookings.service.SessionService;
import dev.iman.astrobookings.session.SessionStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsoleMenuTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);
    private static final String LIST_OPTION_LINE = "1. List launches";
    private static final String EXIT_OPTION_LINE = "0. Exit";
    private static final String LIST_THEN_EXIT = "1\n0\n";
    private static final String REGISTER_ANA = "2\nana\nana@example.com\nsecret\n";
    private static final String LOG_IN_ANA = "3\nana@example.com\nsecret\n";
    private static final Session ANA = new Session("token-1", "ana", "ana@example.com");
    private static final Session BEA = new Session("token-2", "bea", "bea@example.com");
    private static final String ANA_BODY = """
            {"user":{"username":"ana","email":"ana@example.com"},"accessToken":"token-1"}
            """;
    private static final String NEW_EMAIL_BODY = """
            {"user":{"username":"ana","email":"new@example.com"},"accessToken":"token-2"}
            """;
    private static final String EMAIL_TAKEN_BODY = """
            {"message":"Invalid data","errors":[{"field":"email","message":"Email is already registered"}]}
            """;
    private static final String LOGIN_REJECTED_BODY = "{\"message\":\"Authentication failed\",\"errors\":[]}";
    private static final String ANA_LOGGED_IN_LINE = "Logged in as ana (ana@example.com)";
    private static final String BEA_LOGGED_IN_LINE = "Logged in as bea (bea@example.com)";
    private static final String NEW_EMAIL_LOGGED_IN_LINE = "Logged in as ana (new@example.com)";
    private static final String LOGGED_OUT_LINE = "Logged out.";
    private static final String USERNAME_PROMPT = "Username:";
    private static final String PASSWORD_PROMPT = "Password:";
    private static final String TERMS_PROMPT = "Do you accept the terms? (y/n):";
    private static final String ANOTHER_EMAIL_PROMPT = "Enter another email:";
    private static final String REGISTER_OFFER_PROMPT = "Do you want to register? (y/n):";
    private static final String TERMS_DECLINED_LINE = "The terms were not accepted, so you were not registered.";
    private static final String SESSION_NOT_SAVED_LINE = "The session could not be saved.";
    private static final String CREDENTIALS_REJECTED_LINE = "The email or password was not accepted.";
    private static final String SESSION_REJECTED_LINE = "Your session is no longer valid. Please register again.";
    private static final String BOOK_OPTION_LINE = "5. Book seats";
    private static final String LAUNCH_PROMPT = "Launch number (0 to cancel):";
    private static final String SEATS_PROMPT = "Seats (0 to cancel):";
    private static final String NO_SESSION_LINE = "Register or log in first.";
    private static final String NO_BOOKING_LINE = "No booking was created.";
    private static final String GENERIC_ERROR_LINE = "The API returned an unexpected response.";
    private static final String CAPACITY_LINE = "Capacity exceeded. Available: 2, Requested: 3";
    private static final String CAPACITY_BODY = "{\"message\":\"" + CAPACITY_LINE + "\",\"errors\":[]}";
    private static final String TWO_OPEN_LAUNCHES =
            FakeApiServer.activityJson("Moon", "published") + "," + FakeApiServer.activityJson("Mars", "confirmed");
    private static final String BOOKING_BODY = """
            {"id":"booking-1","activityId":"id-Mars","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"paid"}
            """;
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path folder;

    private FakeApiServer fakeApi;
    private Path sessionFile;

    @BeforeEach
    void startServer() throws IOException {
        fakeApi = FakeApiServer.start();
        sessionFile = folder.resolve("session.json");
    }

    @AfterEach
    void stopServer() {
        fakeApi.stop();
    }

    @Test
    void should_show_the_list_and_exit_options_when_started() {
        List<String> lines = runMenu("0\n");

        assertTrue(lines.contains(LIST_OPTION_LINE));
        assertTrue(lines.contains(EXIT_OPTION_LINE));
    }

    @Test
    void should_make_no_http_call_when_the_list_option_is_not_selected() {
        runMenu("9\n0\n");

        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_show_an_error_and_the_menu_again_when_the_option_is_unknown() {
        List<String> lines = runMenu("9\n0\n");

        assertTrue(lines.contains("Unknown option. Select one of the options in the menu."));
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_show_an_error_and_the_menu_again_when_the_option_is_an_empty_line() {
        List<String> lines = runMenu("\n0\n");

        assertTrue(lines.contains("Unknown option. Select one of the options in the menu."));
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_end_when_the_user_selects_exit() {
        List<String> lines = runMenu("0\n1\n");

        assertEquals(1, timesMenuShown(lines));
        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_end_when_the_input_ends() {
        List<String> lines = runMenu("");

        assertEquals(1, timesMenuShown(lines));
    }

    @Test
    void should_end_when_reading_the_input_fails() {
        InputStream failingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("closed");
            }
        };

        List<String> lines = runMenu(failingInput, TIMEOUT);

        assertEquals(1, timesMenuShown(lines));
    }

    @Test
    void should_show_every_field_of_each_open_launch_when_listing() {
        fakeApi.respondWith(200, """
                [{"id":"id-1","name":"Moon","slug":"moon","price":158,"date":"2026-12-20",
                  "duration":90,"location":"Kourou","minParticipants":3,"maxParticipants":15,"status":"published"},
                 {"id":"id-2","name":"Mars","slug":"mars","price":35.50,"date":"2026-08-25T14:00:00.000Z",
                  "duration":120,"location":"Cape","minParticipants":1,"maxParticipants":2,"status":"confirmed"}]
                """);

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertTrue(lines.containsAll(List.of(
                "Name: Moon",
                "Price: 158",
                "Date: December 20, 2026",
                "Duration: 90",
                "Location: Kourou",
                "Minimum participants: 3",
                "Maximum participants: 15",
                "Status: published")));
        assertTrue(lines.containsAll(List.of(
                "Name: Mars",
                "Price: 35.50",
                "Date: August 25, 2026, 14:00 UTC",
                "Duration: 120",
                "Location: Cape",
                "Minimum participants: 1",
                "Maximum participants: 2",
                "Status: confirmed")));
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_leave_out_other_launches_when_listing() {
        fakeApi.respondWith(200, "["
                + FakeApiServer.activityJson("Moon", "published") + ","
                + FakeApiServer.activityJson("Mars", "draft") + "]");

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertTrue(lines.contains("Name: Moon"));
        assertFalse(lines.contains("Name: Mars"));
    }

    @Test
    void should_show_the_exact_message_when_no_launch_is_open_for_booking() {
        fakeApi.respondWith(200, "[" + FakeApiServer.activityJson("Mars", "draft") + "]");

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertTrue(lines.contains("No launches open for booking."));
    }

    @Test
    void should_not_say_activity_when_showing_its_own_texts() {
        fakeApi.respondWith(200, "[" + FakeApiServer.activityJson("Moon", "published") + "]");
        List<String> listed = runMenu("9\n1\n0\n");
        fakeApi.respondWith(200, "[]");
        List<String> empty = runMenu(LIST_THEN_EXIT);
        fakeApi.respondWith(200, "not json");
        List<String> generic = runMenu(LIST_THEN_EXIT);
        fakeApi.stop();
        List<String> notReachable = runMenu(LIST_THEN_EXIT);

        assertFalse(sayActivity(listed));
        assertFalse(sayActivity(empty));
        assertFalse(sayActivity(generic));
        assertFalse(sayActivity(notReachable));
    }

    @Test
    void should_show_not_reachable_with_the_url_and_the_menu_again_when_the_api_is_down() {
        fakeApi.stop();

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertShownThenMenuAgain(lines, "API not reachable at " + fakeApi.url());
    }

    @Test
    void should_show_not_reachable_and_the_menu_again_when_the_call_times_out() {
        fakeApi.blockResponses();

        List<String> lines = runMenu(scriptOf(LIST_THEN_EXIT), SHORT_TIMEOUT);

        assertShownThenMenuAgain(lines, "API not reachable at " + fakeApi.url());
    }

    @Test
    void should_show_the_message_and_field_errors_and_the_menu_again_when_the_api_responds_with_an_error() {
        fakeApi.respondWith(400, """
                {"message":"Invalid data","errors":[
                  {"field":"name","message":"Too short"},{"field":"price","message":"Too low"}]}
                """);

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertTrue(lines.containsAll(List.of("Invalid data", "name: Too short")));
        assertTrue(lines.indexOf("Invalid data") < lines.indexOf("name: Too short"));
        assertTrue(lines.indexOf("name: Too short") < lines.indexOf("price: Too low"));
        assertShownThenMenuAgain(lines, "price: Too low");
    }

    @Test
    void should_show_the_message_verbatim_when_it_says_activity() {
        fakeApi.respondWith(404, "{\"message\":\"Activity not found\",\"errors\":[]}");

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertShownThenMenuAgain(lines, "Activity not found");
    }

    @Test
    void should_show_the_message_and_the_menu_again_when_the_api_responds_401() {
        fakeApi.respondWith(401, """
                {"message":"Unauthorized","errors":[{"field":"token","message":"Expired"}]}
                """);

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertTrue(lines.contains("Unauthorized"));
        assertShownThenMenuAgain(lines, "token: Expired");
    }

    @Test
    void should_show_a_generic_error_and_the_menu_again_when_the_body_is_not_valid_json() {
        fakeApi.respondWith(200, "<html>not json</html>");

        List<String> lines = runMenu(LIST_THEN_EXIT);

        assertShownThenMenuAgain(lines, "The API returned an unexpected response.");
    }

    @Test
    void should_ask_to_register_again_when_the_session_is_rejected() {
        UnauthorizedFailure unauthorized = new UnauthorizedFailure(new ApiError(401, "Unauthorized", List.of()));

        List<String> lines = linesShownFor(new SessionRejectedFailure(unauthorized));

        assertEquals(List.of(SESSION_REJECTED_LINE), lines);
    }

    @Test
    void should_show_who_is_logged_in_without_calling_the_api_when_started_with_a_saved_session() {
        new SessionStore(sessionFile).write(ANA);

        List<String> lines = runMenu("0\n");

        assertTrue(lines.contains(ANA_LOGGED_IN_LINE));
        assertFalse(lines.contains(USERNAME_PROMPT));
        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_not_show_who_is_logged_in_when_started_without_a_session() {
        List<String> lines = runMenu("0\n");

        assertFalse(isAnyoneShownAsLoggedIn(lines));
    }

    @Test
    void should_tell_the_user_and_continue_with_no_session_when_the_file_cannot_be_read() throws IOException {
        Files.writeString(sessionFile, "not json");

        List<String> lines = runMenu("0\n");

        assertTrue(lines.contains("The session file could not be read. Continuing with no session."));
        assertFalse(isAnyoneShownAsLoggedIn(lines));
        assertTrue(lines.contains(LIST_OPTION_LINE));
        assertFalse(String.join("\n", lines).contains("Exception"));
    }

    @Test
    void should_delete_the_file_and_stop_showing_the_user_when_logging_out() {
        new SessionStore(sessionFile).write(ANA);

        List<String> lines = runMenu("4\n0\n");

        assertFalse(Files.exists(sessionFile));
        assertTrue(lines.indexOf(ANA_LOGGED_IN_LINE) < lines.indexOf(LOGGED_OUT_LINE));
        assertTrue(lines.lastIndexOf(ANA_LOGGED_IN_LINE) < lines.indexOf(LOGGED_OUT_LINE));
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_show_no_error_when_logging_out_without_a_file() {
        List<String> lines = runMenu("4\n0\n");

        assertShownThenMenuAgain(lines, LOGGED_OUT_LINE);
    }

    @Test
    void should_show_the_error_and_the_menu_again_when_the_file_cannot_be_deleted() throws IOException {
        Files.createDirectory(sessionFile);
        Files.writeString(sessionFile.resolve("inside"), "text");

        List<String> lines = runMenu("4\n0\n");

        assertShownThenMenuAgain(lines, "The session file could not be deleted.");
    }

    @Test
    void should_send_the_entered_values_with_terms_true_when_the_user_accepts() throws IOException {
        fakeApi.respondWith(201, ANA_BODY);

        runMenu(REGISTER_ANA + "y\n0\n");

        assertEquals("/users", fakeApi.receivedPath());
        assertEquals(registrationBody("ana@example.com", "secret"), bodyOfRequest(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"y", "Y", "yes", "YES", " yes "})
    void should_accept_the_terms_when_the_answer_is_yes_in_any_case(String answer) {
        fakeApi.respondWith(201, ANA_BODY);

        runMenu(REGISTER_ANA + answer + "\n0\n");

        assertEquals(1, fakeApi.receivedRequests());
    }

    @Test
    void should_keep_the_spaces_of_the_password_when_registering() throws IOException {
        fakeApi.respondWith(201, ANA_BODY);

        runMenu("2\nana\nana@example.com\n  my secret \ny\n0\n");

        assertEquals(registrationBody("ana@example.com", "  my secret "), bodyOfRequest(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "no", "", "maybe"})
    void should_send_nothing_and_start_no_session_when_the_user_declines_the_terms(String answer) {
        List<String> lines = runMenu(REGISTER_ANA + answer + "\n0\n");

        assertEquals(0, fakeApi.receivedRequests());
        assertFalse(isAnyoneShownAsLoggedIn(lines));
        assertFalse(Files.exists(sessionFile));
        assertShownThenMenuAgain(lines, TERMS_DECLINED_LINE);
    }

    @Test
    void should_keep_the_saved_session_when_the_user_declines_the_terms() {
        new SessionStore(sessionFile).write(BEA);

        List<String> lines = runMenu(REGISTER_ANA + "n\n0\n");

        assertEquals(Optional.of(BEA), new SessionStore(sessionFile).read());
        assertTrue(lines.lastIndexOf(BEA_LOGGED_IN_LINE) > lines.indexOf(TERMS_DECLINED_LINE));
    }

    @Test
    void should_save_the_session_without_the_password_and_show_the_user_when_registering_succeeds() throws IOException {
        fakeApi.respondWith(201, ANA_BODY);

        List<String> lines = runMenu(REGISTER_ANA + "y\n0\n");

        assertEquals(Optional.of(ANA), new SessionStore(sessionFile).read());
        assertFalse(Files.readString(sessionFile).contains("secret"));
        assertFalse(String.join("\n", lines).contains("secret"));
        assertShownThenMenuAgain(lines, ANA_LOGGED_IN_LINE);
    }

    @Test
    void should_show_the_field_errors_and_the_menu_again_when_another_field_is_rejected() {
        fakeApi.respondWith(400, """
                {"message":"Invalid data","errors":[{"field":"password","message":"Too short"}]}
                """);

        List<String> lines = runMenu(REGISTER_ANA + "y\n0\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertTrue(lines.contains("Invalid data"));
        assertFalse(lines.contains(ANOTHER_EMAIL_PROMPT));
        assertShownThenMenuAgain(lines, "password: Too short");
    }

    @Test
    void should_show_not_reachable_and_the_menu_again_when_registering_with_the_api_down() {
        fakeApi.stop();

        List<String> lines = runMenu(REGISTER_ANA + "y\n0\n");

        assertShownThenMenuAgain(lines, "API not reachable at " + fakeApi.url());
    }

    @Test
    void should_show_the_error_and_the_menu_again_when_the_session_cannot_be_saved() throws IOException {
        Files.createDirectory(sessionFile);
        fakeApi.respondWith(201, ANA_BODY);

        List<String> lines = runMenu(REGISTER_ANA + "y\n0\n");

        assertFalse(lines.contains(ANA_LOGGED_IN_LINE));
        assertTrue(lines.contains(SESSION_NOT_SAVED_LINE));
        assertEquals(2, timesMenuShown(lines));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2\n", "2\nana\n", "2\nana\nana@example.com\n", "2\nana\nana@example.com\nsecret\n"})
    void should_end_without_a_request_when_the_input_ends_while_registering(String script) {
        List<String> lines = runMenu(script);

        assertEquals(0, fakeApi.receivedRequests());
        assertEquals(2, timesMenuShown(lines));
        assertFalse(lines.contains(TERMS_DECLINED_LINE));
    }

    @Test
    void should_show_the_error_and_resend_with_the_new_email_when_the_email_is_rejected() throws IOException {
        fakeApi.queueResponse(400, EMAIL_TAKEN_BODY);
        fakeApi.queueResponse(201, NEW_EMAIL_BODY);

        List<String> lines = runMenu(REGISTER_ANA + "y\nnew@example.com\n0\n");

        assertTrue(lines.contains("email: Email is already registered"));
        assertTrue(lines.contains(ANOTHER_EMAIL_PROMPT));
        assertEquals(registrationBody("ana@example.com", "secret"), bodyOfRequest(0));
        assertEquals(registrationBody("new@example.com", "secret"), bodyOfRequest(1));
        assertShownThenMenuAgain(lines, NEW_EMAIL_LOGGED_IN_LINE);
    }

    @Test
    void should_ask_again_each_time_the_email_is_rejected() throws IOException {
        fakeApi.queueResponse(400, EMAIL_TAKEN_BODY);
        fakeApi.queueResponse(400, EMAIL_TAKEN_BODY);
        fakeApi.queueResponse(201, NEW_EMAIL_BODY);

        List<String> lines = runMenu(REGISTER_ANA + "y\nother@example.com\nnew@example.com\n0\n");

        assertEquals(2, lines.stream().filter(ANOTHER_EMAIL_PROMPT::equals).count());
        assertEquals(registrationBody("other@example.com", "secret"), bodyOfRequest(1));
        assertEquals(registrationBody("new@example.com", "secret"), bodyOfRequest(2));
    }

    @Test
    void should_not_ask_for_the_other_values_again_when_the_email_is_rejected() {
        fakeApi.queueResponse(400, EMAIL_TAKEN_BODY);
        fakeApi.queueResponse(201, NEW_EMAIL_BODY);

        List<String> lines = runMenu(REGISTER_ANA + "y\nnew@example.com\n0\n");

        assertEquals(1, lines.stream().filter(USERNAME_PROMPT::equals).count());
        assertEquals(1, lines.stream().filter(PASSWORD_PROMPT::equals).count());
        assertEquals(1, lines.stream().filter(TERMS_PROMPT::equals).count());
    }

    @Test
    void should_end_the_option_when_a_resend_is_rejected_only_on_another_field() {
        fakeApi.queueResponse(400, EMAIL_TAKEN_BODY);
        fakeApi.queueResponse(400, """
                {"message":"Invalid data","errors":[{"field":"password","message":"Too short"}]}
                """);

        List<String> lines = runMenu(REGISTER_ANA + "y\nbea@example.com\n0\n");

        assertEquals(2, fakeApi.receivedRequests());
        assertEquals(1, lines.stream().filter(ANOTHER_EMAIL_PROMPT::equals).count());
        assertShownThenMenuAgain(lines, "password: Too short");
    }

    @Test
    void should_end_without_a_request_when_the_input_ends_at_the_new_email_prompt() {
        fakeApi.respondWith(400, EMAIL_TAKEN_BODY);

        List<String> lines = runMenu(REGISTER_ANA + "y\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertTrue(lines.contains(ANOTHER_EMAIL_PROMPT));
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_save_the_session_and_show_the_user_when_logging_in_succeeds() throws IOException {
        fakeApi.respondWith(200, ANA_BODY);

        List<String> lines = runMenu(LOG_IN_ANA + "0\n");

        assertEquals("/login", fakeApi.receivedPath());
        assertEquals(JSON.readTree("{\"email\":\"ana@example.com\",\"password\":\"secret\"}"), bodyOfRequest(0));
        assertEquals(Optional.of(ANA), new SessionStore(sessionFile).read());
        assertFalse(Files.readString(sessionFile).contains("secret"));
        assertShownThenMenuAgain(lines, ANA_LOGGED_IN_LINE);
    }

    @Test
    void should_explain_and_offer_to_register_when_login_responds_401() {
        fakeApi.respondWith(401, LOGIN_REJECTED_BODY);

        List<String> lines = runMenu(LOG_IN_ANA + "n\n0\n");

        assertTrue(lines.contains(CREDENTIALS_REJECTED_LINE));
        assertFalse(lines.contains("Authentication failed"));
        assertTrue(lines.indexOf(CREDENTIALS_REJECTED_LINE) < lines.indexOf(REGISTER_OFFER_PROMPT));
    }

    @Test
    void should_register_when_the_user_accepts_the_offer() throws IOException {
        fakeApi.queueResponse(401, LOGIN_REJECTED_BODY);
        fakeApi.queueResponse(201, ANA_BODY);

        List<String> lines = runMenu(LOG_IN_ANA + "Y\nana\nana@example.com\nsecret\ny\n0\n");

        assertEquals("/users", fakeApi.receivedPath());
        assertEquals(registrationBody("ana@example.com", "secret"), bodyOfRequest(1));
        assertShownThenMenuAgain(lines, ANA_LOGGED_IN_LINE);
    }

    @Test
    void should_return_to_the_menu_when_the_user_declines_the_offer() {
        fakeApi.respondWith(401, LOGIN_REJECTED_BODY);

        List<String> lines = runMenu(LOG_IN_ANA + "n\n0\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertFalse(lines.contains(USERNAME_PROMPT));
        assertShownThenMenuAgain(lines, REGISTER_OFFER_PROMPT);
    }

    @Test
    void should_keep_the_saved_session_when_login_responds_401() {
        new SessionStore(sessionFile).write(BEA);
        fakeApi.respondWith(401, LOGIN_REJECTED_BODY);

        List<String> lines = runMenu(LOG_IN_ANA + "n\n0\n");

        assertEquals(Optional.of(BEA), new SessionStore(sessionFile).read());
        assertTrue(lines.lastIndexOf(BEA_LOGGED_IN_LINE) > lines.indexOf(CREDENTIALS_REJECTED_LINE));
    }

    @Test
    void should_show_not_reachable_and_the_menu_again_when_logging_in_with_the_api_down() {
        fakeApi.stop();

        List<String> lines = runMenu(LOG_IN_ANA + "0\n");

        assertShownThenMenuAgain(lines, "API not reachable at " + fakeApi.url());
    }

    @Test
    void should_ask_for_no_other_email_when_login_has_a_field_error_on_email() {
        fakeApi.respondWith(400, EMAIL_TAKEN_BODY);

        List<String> lines = runMenu(LOG_IN_ANA + "0\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertFalse(lines.contains(ANOTHER_EMAIL_PROMPT));
        assertShownThenMenuAgain(lines, "email: Email is already registered");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3\n", "3\nana@example.com\n"})
    void should_end_without_a_request_when_the_input_ends_while_logging_in(String script) {
        List<String> lines = runMenu(script);

        assertEquals(0, fakeApi.receivedRequests());
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_read_the_password_from_the_prompt_when_there_is_one() throws IOException {
        fakeApi.respondWith(201, ANA_BODY);
        List<String> prompts = new ArrayList<>();
        PasswordPrompt passwordPrompt = prompt -> {
            prompts.add(prompt);
            return Optional.of(" from the console ");
        };

        runMenu("2\nana\nana@example.com\ny\n3\nana@example.com\n0\n", passwordPrompt);

        assertEquals(List.of("Password:", "Password:"), prompts);
        assertEquals(registrationBody("ana@example.com", " from the console "), bodyOfRequest(0));
        assertEquals(
                JSON.readTree("{\"email\":\"ana@example.com\",\"password\":\" from the console \"}"),
                bodyOfRequest(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2\nana\nana@example.com\n", "3\nana@example.com\n"})
    void should_end_without_a_request_when_the_prompt_returns_no_password(String script) {
        List<String> lines = runMenu(script, prompt -> Optional.empty());

        assertEquals(0, fakeApi.receivedRequests());
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_show_the_option_to_book_seats_when_started() {
        List<String> lines = runMenu("0\n");

        assertTrue(lines.contains(BOOK_OPTION_LINE));
    }

    @Test
    void should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_booking_with_no_session() {
        List<String> lines = runMenu("5\n0\n");

        assertEquals(0, fakeApi.receivedRequests());
        assertFalse(lines.contains(LAUNCH_PROMPT));
        assertShownThenMenuAgain(lines, NO_SESSION_LINE);
    }

    @Test
    void should_ask_to_register_or_log_in_when_there_is_no_session() {
        assertEquals(List.of(NO_SESSION_LINE), linesShownFor(new NoSessionFailure()));
    }

    @Test
    void should_say_the_launch_is_not_open_when_the_booking_is_refused() {
        assertEquals(List.of("This launch is not open for booking."), linesShownFor(new LaunchNotBookableFailure()));
    }

    @Test
    void should_number_only_the_open_launches_from_1_when_booking() {
        startSession();
        fakeApi.respondWith(200, "[" + FakeApiServer.activityJson("Venus", "draft") + "," + TWO_OPEN_LAUNCHES + "]");

        List<String> lines = runMenu("5\n0\n0\n");

        assertTrue(lines.contains("1. Moon - December 20, 2026 - Price: 158"));
        assertTrue(lines.contains("2. Mars - December 20, 2026 - Price: 158"));
        assertFalse(String.join("\n", lines).contains("Venus"));
    }

    @Test
    void should_show_the_exact_message_and_ask_for_nothing_when_no_launch_is_open_for_booking() {
        startSession();
        fakeApi.respondWith(200, "[" + FakeApiServer.activityJson("Venus", "draft") + "]");

        List<String> lines = runMenu("5\n0\n");

        assertFalse(lines.contains(LAUNCH_PROMPT));
        assertShownThenMenuAgain(lines, "No launches open for booking.");
    }

    @Test
    void should_show_not_reachable_and_the_menu_again_when_booking_with_the_api_down() {
        startSession();
        fakeApi.stop();

        List<String> lines = runMenu("5\n0\n");

        assertShownThenMenuAgain(lines, "API not reachable at " + fakeApi.url());
    }

    @Test
    void should_say_that_0_cancels_when_asking_for_the_launch_number() {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n0\n0\n");

        assertTrue(lines.contains("Launch number (0 to cancel):"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "00"})
    void should_cancel_without_a_booking_request_when_the_launch_number_is_0(String entry) {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n" + entry + "\n0\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertFalse(lines.contains(SEATS_PROMPT));
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_fetch_a_fresh_list_each_time_the_booking_option_is_selected() {
        startSession();
        fakeApi.queueResponse(200, "[" + FakeApiServer.activityJson("Moon", "published") + "]");
        fakeApi.queueResponse(200, "[" + FakeApiServer.activityJson("Mars", "published") + "]");

        List<String> lines = runMenu("5\n0\n5\n0\n0\n");

        assertEquals(2, fakeApi.receivedRequests());
        assertTrue(lines.contains("1. Mars - December 20, 2026 - Price: 158"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "-1", "abc", "1.5", "", "+1", "١", "99999999999999999999"})
    void should_ask_again_without_a_request_when_the_launch_number_is_invalid(String entry) {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n" + entry + "\n0\n0\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertEquals(2, timesShown(lines, LAUNCH_PROMPT));
        assertTrue(lines.contains("Enter a launch number from the list."));
        assertFalse(String.join("\n", lines).contains("Exception"));
    }

    @Test
    void should_choose_the_launch_by_value_when_the_number_has_leading_zeros() throws IOException {
        startSession();
        respondWithOpenLaunchesThenBooking();

        runMenu("5\n02\n2\n0\n");

        assertEquals("id-Mars", bodyOfRequest(1).get("activityId").asText());
    }

    @Test
    void should_end_without_a_booking_request_when_the_input_ends_at_the_launch_number_prompt() {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_send_the_chosen_launch_id_and_the_seats_when_booking() throws IOException {
        startSession();
        respondWithOpenLaunchesThenBooking();

        runMenu("5\n2\n2\n0\n");

        assertEquals("POST", fakeApi.receivedMethod());
        assertEquals("/bookings", fakeApi.receivedPath());
        assertEquals("Bearer token-1", fakeApi.receivedAuthorization());
        assertEquals(JSON.readTree("{\"activityId\":\"id-Mars\",\"participants\":2}"), bodyOfRequest(1));
    }

    @Test
    void should_show_the_id_launch_name_seats_and_payment_status_when_the_booking_is_created() {
        startSession();
        respondWithOpenLaunchesThenBooking();

        List<String> lines = runMenu("5\n2\n2\n0\n");

        assertTrue(lines.containsAll(List.of(
                "Booking created.", "Booking id: booking-1", "Launch: Mars", "Seats: 2", "Payment status: paid")));
        assertShownThenMenuAgain(lines, "Payment status: paid");
    }

    @Test
    void should_say_that_0_cancels_when_asking_for_the_seats() {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n1\n0\n0\n");

        assertTrue(lines.contains("Seats (0 to cancel):"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "00"})
    void should_cancel_without_a_booking_request_when_the_seats_are_0(String entry) {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n1\n" + entry + "\n0\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertEquals(2, timesMenuShown(lines));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", "1.5", "+1", "", "١"})
    void should_ask_again_without_a_request_when_the_seats_are_invalid(String entry) {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n1\n" + entry + "\n0\n0\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertEquals(2, timesShown(lines, SEATS_PROMPT));
        assertEquals(1, timesShown(lines, LAUNCH_PROMPT));
        assertTrue(lines.contains("The seats must be a whole number of at least 1."));
    }

    @Test
    void should_send_the_seats_when_they_are_too_long_for_an_int() throws IOException {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(400, CAPACITY_BODY);

        List<String> lines = runMenu("5\n1\n99999999999999999999\n0\n");

        assertEquals(new BigInteger("99999999999999999999"), bodyOfRequest(1).get("participants").bigIntegerValue());
        assertShownThenMenuAgain(lines, CAPACITY_LINE);
    }

    @Test
    void should_send_the_seats_by_value_when_they_have_leading_zeros() throws IOException {
        startSession();
        respondWithOpenLaunchesThenBooking();

        runMenu("5\n1\n03\n0\n");

        assertEquals(3, bodyOfRequest(1).get("participants").intValue());
    }

    @Test
    void should_end_without_a_booking_request_when_the_input_ends_at_the_seats_prompt() {
        startSession();
        respondWithOpenLaunches();

        List<String> lines = runMenu("5\n1\n");

        assertEquals(1, fakeApi.receivedRequests());
        assertEquals(2, timesMenuShown(lines));
    }

    @Test
    void should_not_say_activity_when_showing_the_booking_texts() {
        startSession();
        respondWithOpenLaunchesThenBooking();

        List<String> lines = runMenu("5\n9\n1\nabc\n2\n0\n");

        assertTrue(lines.contains("Booking created."));
        assertFalse(sayActivity(lines));
    }

    @Test
    void should_show_the_capacity_message_unchanged_and_the_menu_again_when_the_capacity_is_exceeded() {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(400, CAPACITY_BODY);

        List<String> lines = runMenu("5\n1\n3\n0\n");

        assertShownThenMenuAgain(lines, CAPACITY_LINE);
        assertFalse(lines.contains(NO_BOOKING_LINE));
    }

    @Test
    void should_show_the_message_and_say_no_booking_was_created_when_the_payment_is_rejected() {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(402, "{\"message\":\"Payment could not be processed\",\"errors\":[]}");

        List<String> lines = runMenu("5\n1\n2\n0\n");

        assertTrue(lines.indexOf("Payment could not be processed") < lines.indexOf(NO_BOOKING_LINE));
        assertTrue(lines.contains("Payment could not be processed"));
        assertShownThenMenuAgain(lines, NO_BOOKING_LINE);
    }

    @Test
    void should_say_no_booking_was_created_when_the_402_body_is_not_valid_json() {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(402, "<html>");

        List<String> lines = runMenu("5\n1\n2\n0\n");

        assertTrue(lines.contains(GENERIC_ERROR_LINE));
        assertShownThenMenuAgain(lines, NO_BOOKING_LINE);
    }

    @Test
    void should_show_the_message_verbatim_when_booking_on_a_launch_the_api_cannot_find() {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(404, "{\"message\":\"Activity not found\",\"errors\":[]}");

        List<String> lines = runMenu("5\n1\n2\n0\n");

        assertShownThenMenuAgain(lines, "Activity not found");
        assertFalse(lines.contains(NO_BOOKING_LINE));
    }

    @Test
    void should_delete_the_file_and_ask_to_register_again_when_booking_responds_401() {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");

        List<String> lines = runMenu("5\n1\n2\n0\n");

        assertFalse(Files.exists(sessionFile));
        assertFalse(lines.contains("Unauthorized"));
        assertShownThenMenuAgain(lines, SESSION_REJECTED_LINE);
    }

    @Test
    void should_ask_to_register_or_log_in_when_booking_again_after_a_rejected_session() {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");

        List<String> lines = runMenu("5\n1\n2\n5\n0\n");

        assertEquals(2, fakeApi.receivedRequests());
        assertTrue(lines.indexOf(SESSION_REJECTED_LINE) < lines.indexOf(NO_SESSION_LINE));
        assertEquals(3, timesMenuShown(lines));
    }

    @Test
    void should_show_a_generic_error_and_the_menu_again_when_the_booking_response_is_malformed() {
        startSession();
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(201, "{\"id\":\"booking-1\"}");

        List<String> lines = runMenu("5\n1\n2\n0\n");

        assertFalse(lines.contains("Booking created."));
        assertShownThenMenuAgain(lines, GENERIC_ERROR_LINE);
    }

    private void startSession() {
        new SessionStore(sessionFile).write(ANA);
    }

    private void respondWithOpenLaunches() {
        fakeApi.respondWith(200, "[" + TWO_OPEN_LAUNCHES + "]");
    }

    private void respondWithOpenLaunchesThenBooking() {
        fakeApi.queueResponse(200, "[" + TWO_OPEN_LAUNCHES + "]");
        fakeApi.respondWith(201, BOOKING_BODY);
    }

    private List<String> linesShownFor(ApiFailure failure) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), TIMEOUT);
        SessionService sessionService = new SessionService(apiClient, new SessionStore(sessionFile));
        LaunchService launchService = new LaunchService(apiClient);
        BookingService bookingService = new BookingService(apiClient, launchService, sessionService);

        new ConsoleMenu(launchService, sessionService, bookingService, scriptOf(""), printStreamOver(captured))
                .showFailure(failure);

        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private static long timesShown(List<String> lines, String expectedLine) {
        return lines.stream().filter(expectedLine::equals).count();
    }

    private JsonNode bodyOfRequest(int index) throws IOException {
        return JSON.readTree(fakeApi.receivedBodies().get(index));
    }

    private static JsonNode registrationBody(String email, String password) {
        return JSON.createObjectNode()
                .put("username", "ana")
                .put("email", email)
                .put("password", password)
                .put("terms", true);
    }

    private static boolean isAnyoneShownAsLoggedIn(List<String> lines) {
        return lines.stream().anyMatch(line -> line.startsWith("Logged in as"));
    }

    private static void assertShownThenMenuAgain(List<String> lines, String expectedLine) {
        assertTrue(lines.contains(expectedLine), () -> "Missing line: " + expectedLine + " in " + lines);
        assertTrue(lines.indexOf(expectedLine) < lines.lastIndexOf(LIST_OPTION_LINE));
        assertEquals(2, timesMenuShown(lines));
        assertFalse(String.join("\n", lines).contains("Exception"));
    }

    private static long timesMenuShown(List<String> lines) {
        return lines.stream().filter(LIST_OPTION_LINE::equals).count();
    }

    private static boolean sayActivity(List<String> lines) {
        return String.join("\n", lines).toLowerCase(Locale.ROOT).contains("activity");
    }

    private static InputStream scriptOf(String script) {
        return new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> runMenu(String script) {
        return runMenu(scriptOf(script), TIMEOUT);
    }

    private List<String> runMenu(InputStream input, Duration timeout) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), timeout);
        SessionService sessionService = new SessionService(apiClient, new SessionStore(sessionFile));
        LaunchService launchService = new LaunchService(apiClient);
        BookingService bookingService = new BookingService(apiClient, launchService, sessionService);

        new ConsoleMenu(launchService, sessionService, bookingService, input, printStreamOver(captured)).run();

        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private List<String> runMenu(String script, PasswordPrompt passwordPrompt) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), TIMEOUT);
        SessionService sessionService = new SessionService(apiClient, new SessionStore(sessionFile));
        LaunchService launchService = new LaunchService(apiClient);
        BookingService bookingService = new BookingService(apiClient, launchService, sessionService);

        new ConsoleMenu(
                        launchService,
                        sessionService,
                        bookingService,
                        scriptOf(script),
                        printStreamOver(captured),
                        passwordPrompt)
                .run();

        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private static PrintStream printStreamOver(ByteArrayOutputStream captured) {
        return new PrintStream(captured, true, StandardCharsets.UTF_8);
    }
}
