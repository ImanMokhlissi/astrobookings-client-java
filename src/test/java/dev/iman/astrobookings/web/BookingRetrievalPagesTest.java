package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingRetrievalPagesTest {

    private static final String DETAIL_PATH = "/bookings/booking-1";
    private static final String BOOKING_BODY = """
            {"id":"booking-1","activityId":"id-1","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"refunded",
             "activity":{"name":"Moon","slug":"moon","price":158.5,"date":"2026-12-20","duration":60,
                         "location":"Base","status":"confirmed"}}
            """;
    private static final String NO_PAYMENT_ID_BODY = """
            {"id":"booking-1","activityId":"id-1","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentStatus":"pending",
             "activity":{"name":"Moon","slug":"moon","price":158,"date":"2026-12-20","duration":60,
                         "location":"Base","status":"confirmed"}}
            """;

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
    void should_request_the_bookings_with_the_bearer_token_when_opening_my_bookings() {
        web.startServerWithSession();

        web.get("/bookings");

        assertEquals("GET", web.fakeApi().receivedMethod());
        assertEquals("/bookings", web.fakeApi().receivedPath());
        assertEquals("Bearer token-1", web.fakeApi().receivedAuthorization());
    }

    @Test
    void should_show_each_booking_with_a_link_to_its_detail_when_opening_my_bookings() {
        web.fakeApi().respondWith(200, "[" + BOOKING_BODY + "]");
        web.startServerWithSession();

        String page = web.get("/bookings").body();

        assertTrue(page.contains("<a href=\"/bookings/booking-1\">booking-1</a>"));
        assertTrue(page.contains("Launch: Moon"));
        assertTrue(page.contains("Seats: 2"));
    }

    @Test
    void should_show_the_payment_status_as_the_api_sends_it() {
        web.fakeApi().respondWith(200, "[" + BOOKING_BODY + "]");
        web.startServerWithSession();

        assertTrue(web.get("/bookings").body().contains("Payment status: refunded"));
    }

    @Test
    void should_show_a_message_when_the_user_has_no_bookings() {
        web.startServerWithSession();

        assertTrue(web.get("/bookings").body().contains("You have no bookings."));
    }

    @Test
    void should_request_the_id_with_the_bearer_token_when_opening_a_detail() {
        web.fakeApi().respondWith(200, BOOKING_BODY);
        web.startServerWithSession();

        web.get(DETAIL_PATH);

        assertEquals(DETAIL_PATH, web.fakeApi().receivedPath());
        assertEquals("Bearer token-1", web.fakeApi().receivedAuthorization());
    }

    @Test
    void should_show_every_field_when_opening_a_detail() {
        web.fakeApi().respondWith(200, BOOKING_BODY);
        web.startServerWithSession();

        String page = web.get(DETAIL_PATH).body();

        assertTrue(page.contains("Booking id: booking-1"));
        assertTrue(page.contains("Seats: 2"));
        assertTrue(page.contains("Created: November 5, 2026, 10:00 UTC"));
        assertTrue(page.contains("Payment id: payment-1"));
        assertTrue(page.contains("Payment status: refunded"));
        assertTrue(page.contains("Launch: Moon"));
        assertTrue(page.contains("Price: 158.5"));
        assertTrue(page.contains("Date: December 20, 2026"));
        assertTrue(page.contains("Duration: 60"));
        assertTrue(page.contains("Location: Base"));
        assertTrue(page.contains("Launch status: confirmed"));
    }

    @Test
    void should_say_there_is_no_payment_id_when_the_booking_has_none() {
        web.fakeApi().respondWith(200, NO_PAYMENT_ID_BODY);
        web.startServerWithSession();

        assertTrue(web.get(DETAIL_PATH).body().contains("Payment id: none"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings", DETAIL_PATH})
    void should_tell_the_user_to_register_or_log_in_with_no_request_when_opening_either_page_with_no_session(
            String path) {
        web.startServer();

        String page = web.get(path).body();

        assertTrue(page.contains("Register or log in first."));
        assertTrue(page.contains("href=\"/register\">Register"));
        assertTrue(page.contains("href=\"/login\">Log in"));
        assertEquals(0, web.fakeApi().receivedRequests());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings", DETAIL_PATH})
    void should_delete_the_file_and_ask_to_register_again_with_a_link_when_either_page_responds_401(String path) {
        web.fakeApi().respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");
        web.startServerWithSession();

        String page = web.get(path).body();

        assertTrue(page.contains("Your session is no longer valid. Please register again."));
        assertTrue(page.contains("href=\"/register\">Register"));
        assertFalse(page.contains("Logged in as"));
        assertFalse(Files.exists(web.sessionFile()));
    }

    @Test
    void should_show_the_message_when_the_booking_is_not_found() {
        web.fakeApi().respondWith(404, "{\"message\":\"Booking not found\",\"errors\":[]}");
        web.startServerWithSession();

        assertTrue(web.get(DETAIL_PATH).body().contains("Booking not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings", DETAIL_PATH})
    void should_show_not_reachable_when_opening_either_page_with_the_api_down(String path) {
        web.startServerWithSession();
        web.fakeApi().stop();

        String page = web.get(path).body();

        assertTrue(page.contains("API not reachable at " + web.fakeApi().url()));
        assertFalse(page.contains("Exception"));
        assertTrue(Files.exists(web.sessionFile()));
    }

    @Test
    void should_keep_an_encoded_slash_in_one_segment_when_opening_a_detail() {
        web.fakeApi().respondWith(200, BOOKING_BODY);
        web.startServerWithSession();

        web.get("/bookings/a%2Fb+c");

        assertEquals("/bookings/a%2Fb%2Bc", web.fakeApi().receivedRawPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings/.", "/bookings/..", "/bookings/%2E%2E", "/bookings/", "/bookings/a/b", "/bookings/%FF"})
    void should_answer_404_with_no_api_call_when_the_id_is_a_dot_segment(String path) {
        web.startServerWithSession();

        HttpResponse<String> response = web.get(path);

        assertEquals(404, response.statusCode());
        assertEquals(0, web.fakeApi().receivedRequests());
    }

    @Test
    void should_escape_an_id_with_markup_in_the_link_and_the_text() {
        web.fakeApi().respondWith(200, "[" + BOOKING_BODY.replace("\"booking-1\"", "\"<b>'x\\\"\"") + "]");
        web.startServerWithSession();

        String page = web.get("/bookings").body();

        assertTrue(page.contains("<a href=\"/bookings/%3Cb%3E%27x%22\">&lt;b&gt;&#39;x&quot;</a>"));
        assertFalse(page.contains("<b>"));
    }
}
