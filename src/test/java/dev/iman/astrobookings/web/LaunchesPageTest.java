package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.FakeApiServer;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LaunchesPageTest {

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
    void should_show_every_field_of_an_open_launch_when_opening_the_launches_page() {
        web.fakeApi().respondWith(200, "[" + FakeApiServer.activityJson("Moon", "confirmed") + "]");
        web.startServer();

        String page = web.get("/").body();

        assertTrue(page.contains("Name: Moon"));
        assertTrue(page.contains("Price: 158"));
        assertTrue(page.contains("Date: December 20, 2026"));
        assertTrue(page.contains("Duration: 60"));
        assertTrue(page.contains("Location: Base"));
        assertTrue(page.contains("Minimum participants: 2"));
        assertTrue(page.contains("Maximum participants: 8"));
        assertTrue(page.contains("Status: confirmed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "sold-out", "done", "cancelled", "unknown"})
    void should_leave_out_a_launch_when_its_status_is_not_open(String status) {
        web.fakeApi().respondWith(200, "["
                + FakeApiServer.activityJson("Hidden", status) + ","
                + FakeApiServer.activityJson("Moon", "published") + "]");
        web.startServer();

        String page = web.get("/").body();

        assertFalse(page.contains("Hidden"));
        assertTrue(page.contains("Moon"));
    }

    @Test
    void should_show_the_no_launches_message_when_none_qualifies() {
        web.fakeApi().respondWith(200, "[" + FakeApiServer.activityJson("Hidden", "draft") + "]");
        web.startServer();

        assertTrue(web.get("/").body().contains("No launches open for booking."));
    }

    @Test
    void should_escape_markup_when_a_launch_has_it_in_its_text() {
        web.fakeApi().respondWith(200, "[" + FakeApiServer.activityJson("<b>Moon</b>", "published") + "]");
        web.startServer();

        String page = web.get("/").body();

        assertTrue(page.contains("&lt;b&gt;Moon&lt;/b&gt;"));
        assertFalse(page.contains("<b>Moon"));
    }

    @Test
    void should_keep_non_ascii_text_when_a_launch_has_it() {
        web.fakeApi().respondWith(200, "[" + FakeApiServer.activityJson("Órbita Ñandú", "published") + "]");
        web.startServer();

        assertTrue(web.get("/").body().contains("Órbita Ñandú"));
    }

    @Test
    void should_show_not_reachable_with_the_configured_url_when_the_api_is_down() {
        web.startServer();
        web.fakeApi().stop();

        String page = web.get("/").body();

        assertTrue(page.contains("API not reachable at " + web.fakeApi().url()));
        assertFalse(page.contains("Exception"));
    }

    @Test
    void should_show_not_reachable_when_the_api_call_times_out() {
        web.fakeApi().blockResponses();
        web.startServerWithTimeout(Duration.ofMillis(200));

        assertTrue(web.get("/").body().contains("API not reachable at " + web.fakeApi().url()));
    }

    @Test
    void should_show_the_message_and_each_field_error_when_the_api_responds_with_an_error() {
        web.fakeApi().respondWith(
                500, "{\"message\":\"Failed <now>\",\"errors\":[{\"field\":\"name\",\"message\":\"is wrong\"}]}");
        web.startServer();

        String page = web.get("/").body();

        assertTrue(page.contains("Failed &lt;now&gt;"));
        assertTrue(page.contains("name: is wrong"));
    }

    @Test
    void should_show_a_generic_error_with_no_stack_trace_when_the_response_is_not_json() {
        web.fakeApi().respondWith(200, "<html>not json</html>");
        web.startServer();

        var response = web.get("/");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("The API returned an unexpected response."));
        assertFalse(response.body().contains("Exception"));
    }
}
