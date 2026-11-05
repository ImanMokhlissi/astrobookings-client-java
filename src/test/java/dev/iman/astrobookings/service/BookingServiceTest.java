package dev.iman.astrobookings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiNotReachableFailure;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.LaunchNotBookableFailure;
import dev.iman.astrobookings.model.NoSessionFailure;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.SessionRejectedFailure;
import dev.iman.astrobookings.session.SessionStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BookingServiceTest {

    private static final Session ANA = new Session("token-1", "ana", "ana@example.com");
    private static final BigInteger TWO_SEATS = BigInteger.TWO;
    private static final String BOOKING_BODY = """
            {"id":"booking-1","activityId":"id-1","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"paid"}
            """;
    private static final String RETRIEVED_BOOKING_BODY = """
            {"id":"booking-1","activityId":"id-1","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"paid",
             "activity":{"name":"Moon","slug":"moon","price":10,"date":"2026-12-20","duration":60,
                         "location":"Base","status":"published"}}
            """;

    @TempDir
    Path folder;

    private FakeApiServer fakeApi;
    private Path file;
    private SessionStore store;
    private SessionService sessionService;
    private BookingService bookingService;

    @BeforeEach
    void setUp() throws IOException {
        fakeApi = FakeApiServer.start();
        file = folder.resolve("session.json");
        store = new SessionStore(file);
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), Duration.ofSeconds(10));
        sessionService = new SessionService(apiClient, store);
        bookingService = new BookingService(apiClient, new LaunchService(apiClient), sessionService);
    }

    @AfterEach
    void stopServer() {
        fakeApi.stop();
    }

    @Test
    void should_fail_as_no_session_without_calling_the_api_when_listing_with_no_session() {
        assertThrows(NoSessionFailure.class, bookingService::listBookableLaunches);

        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_return_only_published_and_confirmed_launches_when_listing() {
        startSession();
        respondWithStatuses("draft", "published", "sold-out", "confirmed", "done", "cancelled", "unknown");

        List<Launch> launches = bookingService.listBookableLaunches();

        assertEquals(List.of("published", "confirmed"), launches.stream().map(Launch::status).toList());
    }

    @Test
    void should_return_no_launches_when_none_is_open() {
        startSession();
        respondWithStatuses("draft", "done");

        assertEquals(List.of(), bookingService.listBookableLaunches());
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "sold-out", "done", "cancelled", "unknown"})
    void should_fail_as_not_bookable_without_calling_the_api_when_the_launch_is_not_open(String status) {
        startSession();
        Launch launch = launchWithStatus(status);

        assertThrows(LaunchNotBookableFailure.class, () -> bookingService.book(launch, TWO_SEATS));

        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_fail_as_no_session_without_calling_the_api_when_booking_with_no_session() {
        Launch launch = launchWithStatus("published");

        assertThrows(NoSessionFailure.class, () -> bookingService.book(launch, TWO_SEATS));

        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_send_the_saved_token_when_booking() {
        startSession();
        fakeApi.respondWith(201, BOOKING_BODY);

        bookingService.book(launchWithStatus("published"), TWO_SEATS);

        assertEquals("Bearer token-1", fakeApi.receivedAuthorization());
    }

    @Test
    void should_return_the_booking_when_the_launch_is_published() {
        startSession();
        fakeApi.respondWith(201, BOOKING_BODY);

        Booking booking = bookingService.book(launchWithStatus("published"), TWO_SEATS);

        assertEquals("booking-1", booking.id());
    }

    @Test
    void should_return_the_booking_when_the_launch_is_confirmed() {
        startSession();
        fakeApi.respondWith(201, BOOKING_BODY);

        Booking booking = bookingService.book(launchWithStatus("confirmed"), TWO_SEATS);

        assertEquals("booking-1", booking.id());
    }

    @Test
    void should_delete_the_file_and_fail_as_session_rejected_when_booking_responds_401() {
        startSession();
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");
        Launch launch = launchWithStatus("published");

        assertThrows(SessionRejectedFailure.class, () -> bookingService.book(launch, TWO_SEATS));

        assertFalse(Files.exists(file));
        assertTrue(sessionService.currentSession().isEmpty());
    }

    @Test
    void should_keep_the_session_when_booking_fails_otherwise() {
        startSession();
        fakeApi.respondWith(400, "{\"message\":\"Capacity exceeded. Available: 1, Requested: 2\",\"errors\":[]}");
        Launch launch = launchWithStatus("published");

        assertThrows(ApiErrorFailure.class, () -> bookingService.book(launch, TWO_SEATS));

        assertEquals(Optional.of(ANA), store.read());
        assertEquals(Optional.of(ANA), sessionService.currentSession());
    }

    @ParameterizedTest
    @EnumSource(Retrieval.class)
    void should_fail_as_no_session_without_calling_the_api_when_retrieving_with_no_session(Retrieval retrieval) {
        assertThrows(NoSessionFailure.class, () -> retrieval.runOn(bookingService));

        assertEquals(0, fakeApi.receivedRequests());
    }

    @ParameterizedTest
    @EnumSource(Retrieval.class)
    void should_send_the_saved_token_when_retrieving(Retrieval retrieval) {
        startSession();
        fakeApi.respondWith(200, retrieval.successBody());

        retrieval.runOn(bookingService);

        assertEquals("Bearer token-1", fakeApi.receivedAuthorization());
    }

    @ParameterizedTest
    @EnumSource(Retrieval.class)
    void should_delete_the_file_and_fail_as_session_rejected_when_retrieving_responds_401(Retrieval retrieval) {
        startSession();
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");

        assertThrows(SessionRejectedFailure.class, () -> retrieval.runOn(bookingService));

        assertFalse(Files.exists(file));
        assertTrue(sessionService.currentSession().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Retrieval.class)
    void should_keep_the_session_when_retrieving_fails_otherwise(Retrieval retrieval) {
        startSession();
        fakeApi.respondWith(500, "{\"message\":\"Failed to retrieve bookings\",\"errors\":[]}");

        assertThrows(ApiErrorFailure.class, () -> retrieval.runOn(bookingService));

        assertEquals(Optional.of(ANA), store.read());
        assertEquals(Optional.of(ANA), sessionService.currentSession());
    }

    @ParameterizedTest
    @EnumSource(Retrieval.class)
    void should_keep_the_session_when_retrieving_finds_the_api_down(Retrieval retrieval) {
        startSession();
        fakeApi.stop();

        assertThrows(ApiNotReachableFailure.class, () -> retrieval.runOn(bookingService));

        assertEquals(Optional.of(ANA), store.read());
        assertEquals(Optional.of(ANA), sessionService.currentSession());
    }

    @Test
    void should_return_the_bookings_when_listing_them() {
        startSession();
        fakeApi.respondWith(200, "[" + RETRIEVED_BOOKING_BODY + "]");

        List<Booking> bookings = bookingService.listBookings();

        assertEquals(List.of("booking-1"), bookings.stream().map(Booking::id).toList());
    }

    @Test
    void should_return_the_booking_when_getting_it_by_its_id() {
        startSession();
        fakeApi.respondWith(200, RETRIEVED_BOOKING_BODY);

        Booking booking = bookingService.getBooking("booking-1");

        assertEquals("booking-1", booking.id());
        assertEquals("/bookings/booking-1", fakeApi.receivedPath());
    }

    /** The two retrieval use cases, so that a test about the session runs for both. */
    enum Retrieval {
        LIST_BOOKINGS {
            @Override
            void runOn(BookingService bookingService) {
                bookingService.listBookings();
            }

            @Override
            String successBody() {
                return "[" + RETRIEVED_BOOKING_BODY + "]";
            }
        },
        GET_BOOKING {
            @Override
            void runOn(BookingService bookingService) {
                bookingService.getBooking("booking-1");
            }

            @Override
            String successBody() {
                return RETRIEVED_BOOKING_BODY;
            }
        };

        abstract void runOn(BookingService bookingService);

        abstract String successBody();
    }

    private void startSession() {
        store.write(ANA);
        sessionService.loadSavedSession();
    }

    private void respondWithStatuses(String... statuses) {
        String activities = Stream.of(statuses)
                .map(status -> FakeApiServer.activityJson("Launch-" + status, status))
                .collect(Collectors.joining(",", "[", "]"));
        fakeApi.respondWith(200, activities);
    }

    private static Launch launchWithStatus(String status) {
        return new Launch("id-1", "Moon", "moon", BigDecimal.TEN, "2026-12-20", 60, "Base", 1, 8, status);
    }
}
