package dev.iman.astrobookings.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.service.LaunchService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsoleMenuTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);
    private static final String LIST_OPTION_LINE = "1. List launches";
    private static final String EXIT_OPTION_LINE = "0. Exit";
    private static final String LIST_THEN_EXIT = "1\n0\n";

    private FakeApiServer fakeApi;

    @BeforeEach
    void startServer() throws IOException {
        fakeApi = FakeApiServer.start();
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
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), timeout);

        new ConsoleMenu(new LaunchService(apiClient), input, output).run();

        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }
}
