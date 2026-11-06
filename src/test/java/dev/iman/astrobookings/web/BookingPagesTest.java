package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.FakeApiServer;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingPagesTest {

    private static final String MOON_LIST = "[" + FakeApiServer.activityJson("Moon", "published") + "]";
    private static final String CREATED_BODY = """
            {"id":"booking-1","activityId":"id-Moon","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"paid"}
            """;
    private static final String DETAIL_BODY = """
            {"id":"booking-1","activityId":"id-Moon","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"paid",
             "activity":{"name":"Moon","slug":"moon","price":158,"date":"2026-12-20","duration":60,
                         "location":"Base","status":"published"}}
            """;
    private static final String CAPACITY_MESSAGE = "Capacity exceeded. Available: 1, Requested: 2";

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
    void should_offer_only_the_open_launches_when_opening_the_booking_page() {
        web.fakeApi().respondWith(200, "["
                + FakeApiServer.activityJson("Moon", "published") + ","
                + FakeApiServer.activityJson("Mars", "confirmed") + ","
                + FakeApiServer.activityJson("Hidden", "draft") + "]");
        web.startServerWithSession();

        String page = web.get("/book").body();

        assertTrue(page.contains("<option value=\"id-Moon\">"));
        assertTrue(page.contains("<option value=\"id-Mars\">"));
        assertTrue(page.contains("name=\"seats\""));
        assertFalse(page.contains("Hidden"));
    }

    @Test
    void should_show_the_no_launches_message_when_none_can_be_booked() {
        web.fakeApi().respondWith(200, "[" + FakeApiServer.activityJson("Hidden", "draft") + "]");
        web.startServerWithSession();

        String page = web.get("/book").body();

        assertTrue(page.contains("No launches open for booking."));
        assertFalse(page.contains("name=\"seats\""));
    }

    @Test
    void should_send_the_launch_id_and_seats_with_the_bearer_token_when_booking() {
        queueSuccessfulBooking();
        web.startServerWithSession();

        postBooking("id-Moon", " 2 ");

        String body = web.fakeApi().receivedBodies().get(1);
        assertEquals("POST", web.fakeApi().receivedMethod());
        assertEquals("/bookings", web.fakeApi().receivedPath());
        assertEquals("Bearer token-1", web.fakeApi().receivedAuthorization());
        assertTrue(body.contains("\"activityId\":\"id-Moon\""));
        assertTrue(body.contains("\"participants\":2"));
    }

    @Test
    void should_redirect_with_303_to_a_relative_path_when_the_api_responds_201() {
        queueSuccessfulBooking();
        web.startServerWithSession();

        HttpResponse<String> response = postBooking("id-Moon", "2");

        assertEquals(303, response.statusCode());
        assertEquals("/bookings/booking-1", response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void should_show_the_booking_on_the_page_it_redirects_to_when_booking() {
        queueSuccessfulBooking();
        web.fakeApi().queueResponse(200, DETAIL_BODY);
        web.startServerWithSession();

        String page = web.followRedirect(postBooking("id-Moon", "2")).body();

        assertTrue(page.contains("Booking id: booking-1"));
        assertTrue(page.contains("Launch: Moon"));
        assertTrue(page.contains("Seats: 2"));
        assertTrue(page.contains("Payment status: paid"));
    }

    @Test
    void should_show_an_error_with_no_booking_request_when_there_is_no_session() {
        web.startServer();

        HttpResponse<String> response = postBooking("id-Moon", "2");

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("Register or log in first."));
        assertEquals(0, web.fakeApi().receivedRequests());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1.5", "-1", "two", "  "})
    void should_show_an_error_with_no_booking_request_when_the_seats_are_invalid(String seats) {
        web.fakeApi().respondWith(200, MOON_LIST);
        web.startServerWithSession();

        HttpResponse<String> response = postBooking("id-Moon", seats);

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("The seats must be a whole number of at least 1."));
        assertTrue(response.body().contains("<option value=\"id-Moon\" selected>"));
        assertEquals("GET", web.fakeApi().receivedMethod());
        assertEquals("/activities", web.fakeApi().receivedPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id-forged", "id-Hidden"})
    void should_show_an_error_with_no_booking_request_when_the_launch_is_not_in_the_fresh_list(String launchId) {
        web.fakeApi().respondWith(200, "["
                + FakeApiServer.activityJson("Moon", "published") + ","
                + FakeApiServer.activityJson("Hidden", "draft") + "]");
        web.startServerWithSession();

        HttpResponse<String> response = postBooking(launchId, "2");

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("This launch is not open for booking."));
        assertEquals(2, web.fakeApi().receivedRequests());
        assertEquals("/activities", web.fakeApi().receivedPath());
    }

    @Test
    void should_show_the_capacity_message_unchanged_on_the_form_when_the_api_responds_400() {
        web.fakeApi().respondWith(200, MOON_LIST);
        web.fakeApi().queueResponse(200, MOON_LIST);
        web.fakeApi().queueResponse(400, "{\"message\":\"" + CAPACITY_MESSAGE + "\",\"errors\":[]}");
        web.startServerWithSession();

        HttpResponse<String> response = postBooking("id-Moon", "2");

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains(CAPACITY_MESSAGE));
        assertTrue(response.body().contains("<option value=\"id-Moon\" selected>"));
        assertTrue(response.body().contains("name=\"seats\" value=\"2\""));
    }

    @Test
    void should_show_the_message_and_that_no_booking_was_created_when_the_api_responds_402() {
        web.fakeApi().respondWith(200, MOON_LIST);
        web.fakeApi().queueResponse(200, MOON_LIST);
        web.fakeApi().queueResponse(402, "{\"message\":\"Payment could not be processed\",\"errors\":[]}");
        web.startServerWithSession();

        HttpResponse<String> response = postBooking("id-Moon", "2");

        assertNotEquals(303, response.statusCode());
        assertTrue(response.body().contains("Payment could not be processed"));
        assertTrue(response.body().contains("No booking was created."));
        assertTrue(response.body().contains("name=\"seats\""));
    }

    @Test
    void should_keep_the_failure_when_the_fresh_list_also_fails_after_a_failed_booking() {
        web.fakeApi().queueResponse(200, MOON_LIST);
        web.fakeApi().queueResponse(400, "{\"message\":\"" + CAPACITY_MESSAGE + "\",\"errors\":[]}");
        web.fakeApi().queueResponse(500, "{\"message\":\"The list failed\",\"errors\":[]}");
        web.startServerWithSession();

        String page = postBooking("id-Moon", "2").body();

        assertTrue(page.contains(CAPACITY_MESSAGE));
        assertFalse(page.contains("name=\"seats\""));
    }

    @Test
    void should_delete_the_file_and_ask_to_register_again_with_a_link_when_booking_responds_401() {
        web.fakeApi().queueResponse(200, MOON_LIST);
        web.fakeApi().queueResponse(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");
        web.startServerWithSession();

        String page = postBooking("id-Moon", "2").body();

        assertTrue(page.contains("Your session is no longer valid. Please register again."));
        assertTrue(page.contains("href=\"/register\">Register"));
        assertFalse(page.contains("name=\"seats\""));
        assertFalse(page.contains("Logged in as"));
        assertFalse(Files.exists(web.sessionFile()));
    }

    @Test
    void should_show_not_reachable_when_booking_with_the_api_down() {
        web.startServerWithSession();
        web.fakeApi().stop();

        String page = postBooking("id-Moon", "2").body();

        assertTrue(page.contains("API not reachable at " + web.fakeApi().url()));
        assertFalse(page.contains("Exception"));
        assertTrue(Files.exists(web.sessionFile()));
    }

    @Test
    void should_escape_the_typed_seats_and_the_launch_id_when_showing_the_form() {
        web.fakeApi().respondWith(200, MOON_LIST);
        web.startServerWithSession();

        String page = postBooking("\"><script>", "\"><b>").body();

        assertTrue(page.contains("value=\"&quot;&gt;&lt;b&gt;\""));
        assertFalse(page.contains("<script"));
        assertFalse(page.contains("<b>"));
    }

    private void queueSuccessfulBooking() {
        web.fakeApi().queueResponse(200, MOON_LIST);
        web.fakeApi().queueResponse(201, CREATED_BODY);
    }

    private HttpResponse<String> postBooking(String launchId, String seats) {
        return web.post("/book", "launchId", launchId, "seats", seats);
    }
}
