package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.model.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules that hold over the whole interface, so each test goes through every page. */
class WebRulesTest {

    private static final String DETAIL_PATH = "/bookings/booking-1";
    private static final List<String> GET_PATHS =
            List.of("/", "/register", "/login", "/book", "/bookings", DETAIL_PATH, "/style.css", "/logout", "/nowhere");

    /** Holds what must never appear unescaped, as JSON text. */
    private static final String MARKUP_JSON = "<script>\\\"'mark";

    private static final String MARKUP = "<script>\"'mark";
    private static final String ESCAPED_MARKUP = "&lt;script&gt;&quot;&#39;mark";
    private static final String CLEAN_LAUNCHES = "[" + FakeApiServer.activityJson("Moon", "published") + "]";
    private static final String CLEAN_BOOKING = """
            {"id":"booking-1","activityId":"id-1","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"paid",
             "activity":{"name":"Moon","slug":"moon","price":158,"date":"2026-12-20","duration":60,
                         "location":"Base","status":"published"}}
            """;
    private static final String MARKUP_BOOKING = """
            {"id":"%1$s","activityId":"id-1","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"%1$s","paymentStatus":"%1$s",
             "activity":{"name":"%1$s","slug":"moon","price":158,"date":"2026-12-20","duration":60,
                         "location":"%1$s","status":"%1$s"}}
            """.formatted(MARKUP_JSON);
    private static final String MARKUP_LAUNCH = """
            {"id":"%1$s","name":"%1$s","slug":"moon","price":158,"date":"2026-12-20","duration":60,
             "location":"%1$s","minParticipants":2,"maxParticipants":8,"status":"published"}
            """.formatted(MARKUP_JSON);
    private static final String MARKUP_ERROR =
            "{\"message\":\"%1$s\",\"errors\":[{\"field\":\"%1$s\",\"message\":\"%1$s\"}]}".formatted(MARKUP_JSON);

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_not_say_activity_in_any_page(boolean withSession) {
        if (withSession) {
            web.saveSession(WebTestFixture.ANA);
        }
        web.startServer();

        web.fakeApi().respondWith(200, CLEAN_LAUNCHES);
        List.of("/", "/register", "/login", "/book", "/style.css", "/logout", "/nowhere")
                .forEach(path -> assertHasNoActivityWord(web.get(path).body()));
        assertHasNoActivityWord(web.post("/register").body());
        assertHasNoActivityWord(web.post("/book", "launchId", "id-Moon", "seats", "0").body());
        web.fakeApi().respondWith(200, "[" + CLEAN_BOOKING + "]");
        assertHasNoActivityWord(web.get("/bookings").body());
        web.fakeApi().respondWith(200, CLEAN_BOOKING);
        assertHasNoActivityWord(web.get(DETAIL_PATH).body());
        web.fakeApi().respondWith(401, "{\"message\":\"Not accepted\",\"errors\":[]}");
        assertHasNoActivityWord(web.post("/login", "email", "a@b.c").body());
    }

    @Test
    void should_escape_markup_from_the_api_in_every_page() {
        web.saveSession(new Session("token-1", MARKUP, MARKUP));
        web.startServer();

        web.fakeApi().respondWith(200, "[" + MARKUP_LAUNCH + "]");
        assertEscaped(web.get("/").body());
        assertEscaped(web.get("/book").body());
        web.fakeApi().respondWith(200, "[" + MARKUP_BOOKING + "]");
        assertEscaped(web.get("/bookings").body());
        web.fakeApi().respondWith(200, MARKUP_BOOKING);
        assertEscaped(web.get(DETAIL_PATH).body());
        web.fakeApi().respondWith(500, MARKUP_ERROR);
        assertEscaped(web.get("/").body());
        assertEscaped(web.get(DETAIL_PATH).body());
        assertEscaped(web.post("/register", "email", MARKUP, "terms", "accepted").body());
        assertEscaped(web.post("/login", "email", MARKUP).body());
        assertEscaped(web.post("/book", "launchId", MARKUP, "seats", "1").body());
        assertEscaped(web.post("/book", "launchId", MARKUP, "seats", MARKUP).body());
    }

    @Test
    void should_not_change_the_session_file_when_any_get_is_requested() throws IOException {
        web.startServerWithSession();
        web.fakeApi().respondWith(200, "[]");
        String contentBefore = Files.readString(web.sessionFile());
        FileTime modifiedBefore = Files.getLastModifiedTime(web.sessionFile());

        GET_PATHS.forEach(web::get);

        assertEquals(contentBefore, Files.readString(web.sessionFile()));
        assertEquals(modifiedBefore, Files.getLastModifiedTime(web.sessionFile()));
    }

    private static void assertHasNoActivityWord(String page) {
        String lowerCasePage = page.toLowerCase(Locale.ROOT);
        assertFalse(lowerCasePage.contains("activit"), page);
    }

    private static void assertEscaped(String page) {
        assertTrue(page.contains(ESCAPED_MARKUP), page);
        assertFalse(page.contains("<script"), page);
        assertFalse(page.contains("\"'mark"), page);
    }
}
