package dev.iman.astrobookings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Acceptance criteria of feat-launch-listing, against the real API. AC3 has no test here because
 * the real API always has a published launch: it is covered by ConsoleMenuTest.
 */
@Tag("e2e")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class LaunchListingE2eTest {

    private static final String VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final String LIST_OPTION_LINE = "1. List launches";
    private static final String EXIT_OPTION_LINE = "0. Exit";
    private static final String UNKNOWN_OPTION_LINE = "Unknown option. Select one of the options in the menu.";
    private static final String GENERIC_ERROR_LINE = "The API returned an unexpected response.";
    private static final String LIST_THEN_EXIT = "1\n0\n";
    private static final Set<String> OPEN_FOR_BOOKING_STATUSES = Set.of("published", "confirmed");
    private static final DateTimeFormatter DATE_DISPLAY =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter INSTANT_DISPLAY =
            DateTimeFormatter.ofPattern("MMMM d, yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    @Test
    void AC1_should_show_the_list_and_exit_options_when_the_client_starts() {
        ClientRun run = runClient(Map.of(VARIABLE, apiUrl()), "0\n");

        assertTrue(run.lines().contains(LIST_OPTION_LINE), run::describe);
        assertTrue(run.lines().contains(EXIT_OPTION_LINE), run::describe);
        assertEquals(0, run.exitCode());
    }

    @Test
    void AC2_should_show_the_fields_of_each_open_launch_and_leave_out_the_rest_when_listing() throws Exception {
        List<Map<String, Object>> activities = readActivitiesFromTheApi();
        List<Map<String, Object>> open = activities.stream().filter(LaunchListingE2eTest::isOpen).toList();
        List<Map<String, Object>> closed = activities.stream().filter(activity -> !isOpen(activity)).toList();
        assertFalse(open.isEmpty(), "The API must hold an open launch for this test to prove anything");
        assertFalse(closed.isEmpty(), "The API must hold a closed launch for this test to prove anything");

        ClientRun run = runClient(Map.of(VARIABLE, apiUrl()), LIST_THEN_EXIT);

        assertEquals(0, run.exitCode());
        for (Map<String, Object> activity : open) {
            List<String> expectedBlock = expectedBlockOf(activity);
            assertTrue(Collections.indexOfSubList(run.lines(), expectedBlock) >= 0,
                    () -> "Missing " + expectedBlock + " in " + run.describe());
        }
        List<Object> openNames = open.stream().map(activity -> activity.get("name")).toList();
        for (Map<String, Object> activity : closed) {
            if (!openNames.contains(activity.get("name"))) {
                assertFalse(run.lines().contains("Name: " + activity.get("name")), run::describe);
            }
        }
        assertEquals(open.size(), run.lines().stream().filter(line -> line.startsWith("Status: ")).count());
        assertSeedDateIsShownAs(open, run, "2026-12-20", "Date: December 20, 2026");
        assertSeedDateIsShownAs(open, run, "2026-08-25T14:00:00.000Z", "Date: August 25, 2026, 14:00 UTC");
    }

    @Test
    void AC4_should_ignore_the_fields_outside_the_constraints_when_the_api_sends_them() throws Exception {
        List<Map<String, Object>> activities = readActivitiesFromTheApi();
        assertTrue(activities.stream().anyMatch(activity -> activity.containsKey("userId")));
        assertTrue(activities.stream().anyMatch(activity -> activity.containsKey("createdAt")));

        ClientRun run = runClient(Map.of(VARIABLE, apiUrl()), LIST_THEN_EXIT);

        assertListsOpenLaunches(run);
        assertFalse(run.lines().contains(GENERIC_ERROR_LINE), run::describe);
        assertNoStackTrace(run);
    }

    @Test
    void AC5_should_not_say_activity_when_showing_its_own_texts() throws IOException {
        List<ClientRun> runs = List.of(
                runClient(Map.of(VARIABLE, apiUrl()), "0\n"),
                runClient(Map.of(VARIABLE, apiUrl()), "9\n0\n"),
                runClient(Map.of(VARIABLE, urlWithNothingListening()), LIST_THEN_EXIT),
                runClient(Map.of(VARIABLE, "not a url"), "0\n"));

        for (ClientRun run : runs) {
            assertFalse(run.stdout().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
            assertFalse(run.stderr().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
        }
    }

    @Test
    void AC6_should_call_the_configured_url_or_the_default_when_it_is_unset_or_blank() throws Exception {
        assertListsOpenLaunches(runClient(Map.of(VARIABLE, apiUrl()), LIST_THEN_EXIT));
        assertListsOpenLaunches(runClient(Map.of(VARIABLE, apiUrl() + "/"), LIST_THEN_EXIT));

        String elsewhere = urlWithNothingListening();
        ClientRun configuredElsewhere = runClient(Map.of(VARIABLE, elsewhere), LIST_THEN_EXIT);
        assertTrue(configuredElsewhere.lines().contains("API not reachable at " + elsewhere),
                configuredElsewhere::describe);

        assumeTrue(DEFAULT_API_URL.equals(apiUrl()), "The API does not run on the default URL");
        assertListsOpenLaunches(runClient(Map.of(), LIST_THEN_EXIT));
        assertListsOpenLaunches(runClient(Map.of(VARIABLE, ""), LIST_THEN_EXIT));
        assertListsOpenLaunches(runClient(Map.of(VARIABLE, "   "), LIST_THEN_EXIT));
    }

    @Test
    void AC7_should_show_not_reachable_and_the_menu_again_when_nothing_listens_or_the_call_times_out()
            throws IOException {
        assertNotReachableThenMenuAgain(urlWithNothingListening());

        // Connections wait in the backlog of a socket that never accepts, so no response ever
        // comes and the timeout that Main wires has to end the call.
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            assertNotReachableThenMenuAgain("http://127.0.0.1:" + silent.getLocalPort());
        }
    }

    /**
     * The real API's only reachable error is the HTML 404 of a wrong path, which is outside AC8's
     * format. The format itself is covered by ConsoleMenuTest; this proves the path back to the menu.
     */
    @Test
    void AC8_should_show_the_generic_error_and_the_menu_again_when_the_real_api_responds_with_a_html_404() {
        ClientRun run = runClient(Map.of(VARIABLE, apiUrl() + "/no-such-path"), LIST_THEN_EXIT);

        assertEquals(0, run.exitCode());
        assertTrue(run.lines().contains(GENERIC_ERROR_LINE), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertTrue(run.lines().indexOf(GENERIC_ERROR_LINE) < run.lines().lastIndexOf(LIST_OPTION_LINE));
        assertNoStackTrace(run);
    }

    @Test
    void AC9_should_show_an_error_and_the_menu_again_when_the_option_is_unknown() {
        for (String option : List.of("9", "2", "abc", "")) {
            ClientRun run = runClient(Map.of(VARIABLE, apiUrl()), option + "\n0\n");

            assertEquals(0, run.exitCode());
            assertTrue(run.lines().contains(UNKNOWN_OPTION_LINE), run::describe);
            assertEquals(2, run.timesMenuShown(), run::describe);
            assertTrue(run.lines().indexOf(UNKNOWN_OPTION_LINE) < run.lines().lastIndexOf(LIST_OPTION_LINE));
            assertNoStackTrace(run);
        }
    }

    @Test
    void AC10_should_end_without_a_stack_trace_when_the_user_selects_exit() {
        ClientRun run = runClient(Map.of(VARIABLE, apiUrl()), "0\n1\n");

        assertEquals(0, run.exitCode());
        assertEquals(1, run.timesMenuShown(), run::describe);
        assertFalse(run.lines().stream().anyMatch(line -> line.startsWith("Name: ")), run::describe);
        assertNoStackTrace(run);
    }

    @Test
    void AC11_should_end_without_a_stack_trace_when_the_input_ends() {
        ClientRun empty = runClient(Map.of(VARIABLE, apiUrl()), "");
        assertEquals(0, empty.exitCode());
        assertEquals(1, empty.timesMenuShown(), empty::describe);
        assertNoStackTrace(empty);

        for (String script : List.of("1\n", "1")) {
            ClientRun afterListing = runClient(Map.of(VARIABLE, apiUrl()), script);
            assertListsOpenLaunches(afterListing);
            assertEquals(2, afterListing.timesMenuShown(), afterListing::describe);
        }
    }

    @Test
    void AC12_should_show_an_error_without_a_stack_trace_and_return_1_when_the_url_is_invalid() {
        List<String> invalidUrls = List.of(
                "not a url",
                "not-a-url",
                "localhost:3000",
                "ftp://localhost",
                "http://",
                "http://local host:3000",
                "http://localhost:99999",
                "http://localhost:0");

        for (String invalidUrl : invalidUrls) {
            ClientRun run = runClient(Map.of(VARIABLE, invalidUrl), "1\n0\n");

            assertEquals(1, run.exitCode(), run::describe);
            assertTrue(run.lines().contains("Invalid API URL in ASTROBOOKINGS_API_URL: " + invalidUrl), run::describe);
            assertEquals(0, run.timesMenuShown(), run::describe);
            assertNoStackTrace(run);
        }
    }

    private static void assertNotReachableThenMenuAgain(String url) {
        ClientRun run = runClient(Map.of(VARIABLE, url), LIST_THEN_EXIT);
        String expectedLine = "API not reachable at " + url;

        assertEquals(0, run.exitCode());
        assertTrue(run.lines().contains(expectedLine), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertTrue(run.lines().indexOf(expectedLine) < run.lines().lastIndexOf(LIST_OPTION_LINE), run::describe);
        assertNoStackTrace(run);
    }

    private static void assertListsOpenLaunches(ClientRun run) {
        assertEquals(0, run.exitCode());
        assertTrue(run.lines().stream().anyMatch(line -> line.startsWith("Name: ")), run::describe);
        assertTrue(run.lines().stream().anyMatch(line -> line.startsWith("Status: published")), run::describe);
    }

    private static void assertNoStackTrace(ClientRun run) {
        for (String text : List.of(run.stdout(), run.stderr())) {
            assertFalse(text.contains("Exception"), run::describe);
            assertFalse(text.contains("Caused by"), run::describe);
            assertFalse(text.contains("\tat "), run::describe);
        }
    }

    private static void assertSeedDateIsShownAs(
            List<Map<String, Object>> open, ClientRun run, String rawDate, String expectedLine) {
        if (open.stream().anyMatch(activity -> rawDate.equals(activity.get("date")))) {
            assertTrue(run.lines().contains(expectedLine), run::describe);
        }
    }

    private static boolean isOpen(Map<String, Object> activity) {
        return OPEN_FOR_BOOKING_STATUSES.contains(activity.get("status"));
    }

    private static List<String> expectedBlockOf(Map<String, Object> activity) {
        return List.of(
                "Name: " + activity.get("name"),
                "Price: " + new BigDecimal(String.valueOf(activity.get("price"))).toPlainString(),
                "Date: " + expectedDateOf(String.valueOf(activity.get("date"))),
                "Duration: " + activity.get("duration"),
                "Location: " + activity.get("location"),
                "Minimum participants: " + activity.get("minParticipants"),
                "Maximum participants: " + activity.get("maxParticipants"),
                "Status: " + activity.get("status"));
    }

    private static String expectedDateOf(String rawDate) {
        try {
            return DATE_DISPLAY.format(LocalDate.parse(rawDate));
        } catch (DateTimeParseException notADate) {
            return INSTANT_DISPLAY.format(Instant.parse(rawDate));
        }
    }

    private static List<Map<String, Object>> readActivitiesFromTheApi() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl() + "/activities")).GET().build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return new ObjectMapper()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(response.body(), new TypeReference<>() {});
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

    private static ClientRun runClient(Map<String, String> environment, String script) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            int exitCode = Main.run(
                    environment,
                    new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                    new PrintStream(stdout, true, StandardCharsets.UTF_8));
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

        long timesMenuShown() {
            return lines().stream().filter(LIST_OPTION_LINE::equals).count();
        }

        String describe() {
            return "exit code " + exitCode + ", stdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }
}
