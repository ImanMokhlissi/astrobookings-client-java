package dev.iman.astrobookings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance criteria of feat-session, against the real API. The login half of AC4 and all of AC9
 * have no test here, because the real API cannot reach them: POST /login always responds 401, and
 * no menu option makes an authenticated call yet. ConsoleMenuTest and SessionServiceTest cover them.
 */
@Tag("e2e")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class SessionE2eTest {

    private static final String VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final String USERNAME = "e2e-user";
    private static final String PASSWORD = "E2e-Secret-Pass-1";
    private static final String USERNAME_PROMPT = "Username:";
    private static final String ANOTHER_EMAIL_PROMPT = "Enter another email:";
    private static final String REGISTER_OFFER_PROMPT = "Do you want to register? (y/n):";
    private static final String LOGGED_IN_PREFIX = "Logged in as ";
    private static final String LOGGED_OUT_LINE = "Logged out.";
    private static final String TERMS_DECLINED_LINE = "The terms were not accepted, so you were not registered.";
    private static final String REPEATED_EMAIL_LINE = "email: Email is already registered";
    private static final String CREDENTIALS_REJECTED_LINE = "The email or password was not accepted.";
    private static final String UNREADABLE_FILE_LINE =
            "The session file could not be read. Continuing with no session.";
    private static final String NOT_DELETED_LINE = "The session file could not be deleted.";
    private static final String NOT_REACHABLE_PREFIX = "API not reachable at ";
    private static final AtomicInteger EMAIL_COUNTER = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryFolder;

    @Test
    void AC1_should_register_against_the_real_api_when_the_user_accepts_the_terms() {
        String email = uniqueEmail();
        Path sessionFile = temporaryFolder.resolve("session.json");

        ClientRun run = runClient(apiUrl(), sessionFile, registerScript(email, "y") + "0\n");

        assertEquals(0, run.exitCode(), run::describe);
        assertTrue(run.lines().contains(loggedInLine(email)), run::describe);
        assertNoStackTrace(run);
    }

    @Test
    void AC2_should_send_nothing_and_start_no_session_when_the_user_does_not_accept_the_terms() {
        for (String answer : List.of("n", "maybe", "")) {
            String email = uniqueEmail();
            Path sessionFile = temporaryFolder.resolve(email + ".json");

            ClientRun declined =runClient(apiUrl(), sessionFile, registerScript(email, answer) + "0\n");

            assertEquals(0, declined.exitCode(), declined::describe);
            assertTrue(declined.lines().contains(TERMS_DECLINED_LINE), declined::describe);
            assertFalse(declined.anyoneLoggedIn(), declined::describe);
            assertFalse(Files.exists(sessionFile), declined::describe);
            assertNoStackTrace(declined);

            // The API refuses a repeated email, so this succeeds only if it never received the first one.
            ClientRun accepted = runClient(apiUrl(), sessionFile, registerScript(email, "y") + "0\n");

            assertFalse(accepted.lines().contains(REPEATED_EMAIL_LINE), accepted::describe);
            assertTrue(accepted.lines().contains(loggedInLine(email)), accepted::describe);
        }
    }

    @Test
    void AC3_should_show_the_error_and_resend_with_the_new_email_when_the_email_is_rejected() throws IOException {
        String repeated = uniqueEmail();
        ClientRun first = runClient(
                apiUrl(), temporaryFolder.resolve("first.json"), registerScript(repeated, "y") + "0\n");
        assertTrue(first.lines().contains(loggedInLine(repeated)), first::describe);

        String replacement = uniqueEmail();
        Path secondFile = temporaryFolder.resolve("second.json");
        ClientRun second = runClient(
                apiUrl(), secondFile, registerScript(repeated, "y") + replacement + "\n0\n");

        assertEquals(0, second.exitCode(), second::describe);
        assertInOrder(second, REPEATED_EMAIL_LINE, ANOTHER_EMAIL_PROMPT, loggedInLine(replacement));
        assertEquals(1, second.lines().stream().filter(USERNAME_PROMPT::equals).count(), second::describe);
        assertEquals(replacement, readJson(secondFile).get("email").asText());
        assertNoStackTrace(second);

        // It asks each time, and also for an email error other than the repeated one.
        String lastEmail = uniqueEmail();
        Path thirdFile = temporaryFolder.resolve("third.json");
        ClientRun third = runClient(
                apiUrl(), thirdFile, registerScript(repeated, "y") + repeated + "\nnot-an-email\n" + lastEmail + "\n0\n");

        assertEquals(3, third.lines().stream().filter(ANOTHER_EMAIL_PROMPT::equals).count(), third::describe);
        assertTrue(third.lines().contains(loggedInLine(lastEmail)), third::describe);
        assertEquals(lastEmail, readJson(thirdFile).get("email").asText());
    }

    @Test
    void AC4_should_save_the_token_username_and_email_but_not_the_password_when_registration_succeeds()
            throws Exception {
        String email = uniqueEmail();
        Path sessionFile = temporaryFolder.resolve("folder-not-created-yet").resolve("session.json");
        assertFalse(Files.exists(sessionFile.getParent()));

        ClientRun run = runClient(apiUrl(), sessionFile, registerScript(email, "y") + "0\n");

        assertTrue(run.lines().contains(loggedInLine(email)), run::describe);
        assertTrue(Files.isRegularFile(sessionFile), run::describe);
        String content = Files.readString(sessionFile);
        assertFalse(content.contains(PASSWORD), content);
        assertFalse(run.stdout().contains(PASSWORD), run::describe);
        JsonNode saved = JSON.readTree(content);
        Set<String> fields = new HashSet<>();
        saved.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("accessToken", "username", "email"), fields, content);
        assertEquals(USERNAME, saved.get("username").asText());
        assertEquals(email, saved.get("email").asText());
        assertEquals(200, statusOfBookingsWith(saved.get("accessToken").asText()), "The saved token must be the API's");
    }

    @Test
    void AC5_should_explain_and_offer_to_register_when_the_real_login_responds_401() {
        String email = uniqueEmail();
        Path sessionFile = temporaryFolder.resolve("session.json");
        String logIn = "3\n" + email + "\n" + PASSWORD + "\n";

        ClientRun declined = runClient(apiUrl(), sessionFile, logIn + "n\n0\n");

        assertEquals(0, declined.exitCode(), declined::describe);
        assertInOrder(declined, CREDENTIALS_REJECTED_LINE, REGISTER_OFFER_PROMPT);
        assertFalse(declined.lines().contains(USERNAME_PROMPT), declined::describe);
        assertFalse(declined.anyoneLoggedIn(), declined::describe);
        assertFalse(Files.exists(sessionFile), declined::describe);
        assertEquals(2, declined.timesMenuShown(), declined::describe);

        String registration = USERNAME + "\n" + email + "\n" + PASSWORD + "\ny\n";
        ClientRun accepted = runClient(apiUrl(), sessionFile, logIn + "y\n" + registration + "0\n");

        assertEquals(0, accepted.exitCode(), accepted::describe);
        assertInOrder(accepted, CREDENTIALS_REJECTED_LINE, REGISTER_OFFER_PROMPT, USERNAME_PROMPT, loggedInLine(email));
        assertTrue(Files.exists(sessionFile), accepted::describe);
        assertNoStackTrace(accepted);
    }

    @Test
    void AC6_should_continue_with_the_saved_session_without_the_api_when_the_client_starts_with_a_readable_file()
            throws IOException {
        String email = uniqueEmail();
        Path sessionFile = temporaryFolder.resolve("session.json");
        runClient(apiUrl(), sessionFile, registerScript(email, "y") + "0\n");
        String savedBefore = Files.readString(sessionFile);

        ClientRun restarted = runClient(urlWithNothingListening(), sessionFile, "0\n");

        assertEquals(0, restarted.exitCode(), restarted::describe);
        assertTrue(restarted.lines().contains(loggedInLine(email)), restarted::describe);
        assertFalse(restarted.lines().contains(USERNAME_PROMPT), restarted::describe);
        assertFalse(restarted.stdout().contains(NOT_REACHABLE_PREFIX), restarted::describe);
        assertEquals(savedBefore, Files.readString(sessionFile));
        assertNoStackTrace(restarted);
    }

    @Test
    void AC7_should_tell_the_user_and_continue_with_no_session_when_the_file_cannot_be_read() throws IOException {
        Map<String, String> unreadableContents = Map.of(
                "invalid-json.json", "{ this is not json",
                "empty.json", "",
                "null-literal.json", "null",
                "missing-field.json", "{\"username\":\"ana\",\"email\":\"ana@example.com\"}");
        for (Map.Entry<String, String> unreadable : unreadableContents.entrySet()) {
            Path sessionFile = temporaryFolder.resolve(unreadable.getKey());
            Files.writeString(sessionFile, unreadable.getValue());

            assertContinuesWithNoSessionAndAWorkingList(sessionFile);
        }

        Path directoryAtThePath = temporaryFolder.resolve("directory.json");
        Files.createDirectory(directoryAtThePath);
        assertContinuesWithNoSessionAndAWorkingList(directoryAtThePath);
    }

    @Test
    void AC8_should_delete_the_file_and_end_the_session_without_an_error_when_the_user_logs_out() {
        String email = uniqueEmail();
        Path sessionFile = temporaryFolder.resolve("session.json");

        // The second log out of this run finds no file.
        ClientRun run = runClient(apiUrl(), sessionFile, registerScript(email, "y") + "4\n4\n0\n");

        assertEquals(0, run.exitCode(), run::describe);
        assertFalse(Files.exists(sessionFile), run::describe);
        assertEquals(2, run.lines().stream().filter(LOGGED_OUT_LINE::equals).count(), run::describe);
        List<String> afterLogOut = run.lines().subList(run.lines().indexOf(LOGGED_OUT_LINE), run.lines().size());
        assertTrue(run.lines().contains(loggedInLine(email)), run::describe);
        assertFalse(afterLogOut.stream().anyMatch(line -> line.startsWith(LOGGED_IN_PREFIX)), run::describe);
        assertFalse(run.lines().contains(NOT_DELETED_LINE), run::describe);
        assertNoStackTrace(run);

        ClientRun noFile = runClient(apiUrl(), sessionFile, "4\n0\n");

        assertEquals(0, noFile.exitCode(), noFile::describe);
        assertTrue(noFile.lines().contains(LOGGED_OUT_LINE), noFile::describe);
        assertFalse(noFile.anyoneLoggedIn(), noFile::describe);
        assertFalse(noFile.lines().contains(NOT_DELETED_LINE), noFile::describe);
        assertNoStackTrace(noFile);
    }

    /** Extends AC5 of feat-launch-listing, on the word "activity", to the texts this spec adds. */
    @Test
    void LaunchListingAC5_should_not_say_activity_when_registering_logging_in_and_logging_out() {
        String email = uniqueEmail();
        Path sessionFile = temporaryFolder.resolve("session.json");
        String logInRejected = "3\n" + uniqueEmail() + "\n" + PASSWORD + "\nn\n";

        ClientRun run = runClient(
                apiUrl(), sessionFile, registerScript(email, "n") + logInRejected + registerScript(email, "y")
                        + registerScript(email, "y") + uniqueEmail() + "\n4\n0\n");

        assertTrue(run.lines().contains(TERMS_DECLINED_LINE), run::describe);
        assertTrue(run.lines().contains(CREDENTIALS_REJECTED_LINE), run::describe);
        assertTrue(run.lines().contains(REPEATED_EMAIL_LINE), run::describe);
        assertTrue(run.lines().contains(LOGGED_OUT_LINE), run::describe);
        assertFalse(run.stdout().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
        assertFalse(run.stderr().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
    }

    private static void assertContinuesWithNoSessionAndAWorkingList(Path sessionFile) {
        ClientRun run = runClient(apiUrl(), sessionFile, "1\n0\n");

        assertEquals(0, run.exitCode(), run::describe);
        assertTrue(run.lines().contains(UNREADABLE_FILE_LINE), run::describe);
        assertFalse(run.anyoneLoggedIn(), run::describe);
        assertTrue(run.lines().stream().anyMatch(line -> line.startsWith("Name: ")), run::describe);
        assertNoStackTrace(run);
    }

    private static void assertInOrder(ClientRun run, String... expectedLines) {
        int previous = -1;
        for (String expected : expectedLines) {
            int found = run.lines().indexOf(expected);
            assertTrue(found > previous, () -> "Expected in order " + List.of(expectedLines) + " in " + run.describe());
            previous = found;
        }
    }

    private static void assertNoStackTrace(ClientRun run) {
        for (String text : List.of(run.stdout(), run.stderr())) {
            assertFalse(text.contains("Exception"), run::describe);
            assertFalse(text.contains("Caused by"), run::describe);
            assertFalse(text.contains("\tat "), run::describe);
        }
    }

    private static String registerScript(String email, String termsAnswer) {
        return "2\n" + USERNAME + "\n" + email + "\n" + PASSWORD + "\n" + termsAnswer + "\n";
    }

    private static String loggedInLine(String email) {
        return LOGGED_IN_PREFIX + USERNAME + " (" + email + ")";
    }

    private static String uniqueEmail() {
        return "e2e-" + System.currentTimeMillis() + "-" + EMAIL_COUNTER.incrementAndGet() + "@example.com";
    }

    private static JsonNode readJson(Path file) throws IOException {
        return JSON.readTree(Files.readString(file));
    }

    private static int statusOfBookingsWith(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl() + "/bookings"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String apiUrl() {
        String configured = System.getenv(VARIABLE);
        return configured == null || configured.isBlank() ? DEFAULT_API_URL : configured;
    }

    private static String urlWithNothingListening() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static ClientRun runClient(String url, Path sessionFile, String script) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            int exitCode = Main.run(
                    Map.of(VARIABLE, url),
                    new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    sessionFile);
            return new ClientRun(
                    exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalErr);
        }
    }

    private record ClientRun(int exitCode, String stdout, String stderr) {

        List<String> lines() {
            return stdout.lines().toList();
        }

        boolean anyoneLoggedIn() {
            return lines().stream().anyMatch(line -> line.startsWith(LOGGED_IN_PREFIX));
        }

        long timesMenuShown() {
            return lines().stream().filter("1. List launches"::equals).count();
        }

        String describe() {
            return "exit code " + exitCode + ", stdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }
}
