package dev.iman.astrobookings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MainTest {

    private static final String VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String LIST_THEN_EXIT = "1\n0\n";

    @TempDir
    Path folder;

    @Test
    void should_return_exit_code_0_when_run_without_the_api() {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        PrintStream output = new PrintStream(new ByteArrayOutputStream());

        int exitCode = Main.run(Map.of(), input, output, sessionFile());

        assertEquals(0, exitCode);
    }

    @Test
    void should_use_the_default_url_when_the_variable_is_unset() {
        assertEquals("http://localhost:3000", Main.readApiUrl(Map.of()));
    }

    @Test
    void should_use_the_default_url_when_the_variable_is_blank() {
        assertEquals("http://localhost:3000", Main.readApiUrl(Map.of(VARIABLE, "   ")));
    }

    @Test
    void should_use_the_configured_url_when_the_variable_is_set() {
        assertEquals("http://launches.example:8080/", Main.readApiUrl(Map.of(VARIABLE, "http://launches.example:8080/")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "not-a-url", "localhost:3000", "ftp://localhost", "http://", "http://localhost:99999", "http://localhost:0"})
    void should_show_an_error_and_return_exit_code_1_when_the_url_is_invalid(String invalidUrl) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exitCode = Main.run(Map.of(VARIABLE, invalidUrl), scriptOf("0\n"), printStreamOver(captured), sessionFile());

        String shown = captured.toString(StandardCharsets.UTF_8);
        assertEquals(1, exitCode);
        assertTrue(shown.contains("Invalid API URL in ASTROBOOKINGS_API_URL: " + invalidUrl));
        assertFalse(shown.contains("Exception"));
        assertFalse(shown.contains("List launches"));
    }

    @Test
    void should_list_launches_from_the_configured_url_when_the_user_selects_the_list() throws IOException {
        FakeApiServer fakeApi = FakeApiServer.start();
        fakeApi.respondWith(200, "[" + FakeApiServer.activityJson("Moon", "published") + "]");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exitCode = Main.run(
                Map.of(VARIABLE, fakeApi.url()), scriptOf(LIST_THEN_EXIT), printStreamOver(captured), sessionFile());
        fakeApi.stop();

        assertEquals(0, exitCode);
        assertEquals("/activities", fakeApi.receivedPath());
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("Name: Moon"));
    }

    @Test
    void should_show_not_reachable_and_return_exit_code_0_when_the_api_is_down() throws IOException {
        FakeApiServer fakeApi = FakeApiServer.start();
        fakeApi.stop();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exitCode = Main.run(
                Map.of(VARIABLE, fakeApi.url()), scriptOf(LIST_THEN_EXIT), printStreamOver(captured), sessionFile());

        String shown = captured.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(shown.contains("API not reachable at " + fakeApi.url()));
        assertFalse(shown.contains("Exception"));
    }

    @Test
    void should_place_the_session_file_in_the_astrobookings_folder_of_the_home_folder() {
        Path homeFolder = Path.of("home");

        Path sessionFile = Main.sessionFileUnder(homeFolder);

        assertEquals(homeFolder.resolve(".astrobookings").resolve("session.json"), sessionFile);
    }

    private Path sessionFile() {
        return folder.resolve("session.json");
    }

    private static ByteArrayInputStream scriptOf(String script) {
        return new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));
    }

    private static PrintStream printStreamOver(ByteArrayOutputStream captured) {
        return new PrintStream(captured, true, StandardCharsets.UTF_8);
    }
}
