package dev.iman.astrobookings.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiNotReachableFailure;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.FieldError;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.LaunchSummary;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApiClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);
    private static final String GENERIC_MESSAGE = "The API returned an unexpected response.";
    private static final String AUTH_BODY = """
            {"user":{"username":"ana","email":"ana@example.com"},"accessToken":"token-1"}
            """;
    private static final String BOOKING_BODY = """
            {"id":"booking-1","activityId":"launch-1","userId":"user-1","participants":2,
             "createdAt":"2026-11-05T10:00:00.000Z","paymentId":"payment-1","paymentStatus":"paid"}
            """;
    private static final String BOOKING_ACTIVITY = """
            {"name":"Moon","slug":"moon","price":158.50,"date":"2026-12-20","duration":90,
             "location":"Kourou","status":"published"}
            """;
    private static final String RETRIEVED_BOOKING = bookingBodyWithActivity(BOOKING_ACTIVITY);
    private static final ObjectMapper JSON = new ObjectMapper();

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
    void should_return_every_launch_with_its_fields_when_the_api_responds_ok() {
        fakeApi.respondWith(200, """
                [{"id":"activity-1","name":"Moon","slug":"moon","price":158.50,"date":"2026-12-20",
                  "duration":90,"location":"Kourou","minParticipants":3,"maxParticipants":15,"status":"draft"},
                 {"id":"activity-2","name":"Mars","slug":"mars","price":35,"date":"2026-08-25T14:00:00.000Z",
                  "duration":120,"location":"Cape","minParticipants":1,"maxParticipants":2,"status":"published"}]
                """);

        List<Launch> launches = apiClient(fakeApi.url()).fetchLaunches();

        assertEquals(
                List.of(
                        new Launch("activity-1", "Moon", "moon", new BigDecimal("158.50"), "2026-12-20", 90, "Kourou", 3, 15, "draft"),
                        new Launch("activity-2", "Mars", "mars", new BigDecimal("35"), "2026-08-25T14:00:00.000Z", 120, "Cape", 1, 2, "published")),
                launches);
    }

    @Test
    void should_send_a_public_get_to_the_activities_path_when_fetching() {
        apiClient(fakeApi.url()).fetchLaunches();

        assertEquals("GET", fakeApi.receivedMethod());
        assertEquals("/activities", fakeApi.receivedPath());
        assertNull(fakeApi.receivedAuthorization());
    }

    @Test
    void should_return_no_launches_when_the_api_responds_with_an_empty_list() {
        fakeApi.respondWith(200, "[]");

        assertEquals(List.of(), apiClient(fakeApi.url()).fetchLaunches());
    }

    @Test
    void should_ignore_the_field_when_an_activity_holds_an_unknown_one() {
        String activity = FakeApiServer.activityJson("Moon", "published");
        fakeApi.respondWith(200, "[" + activity.replace("{", "{\"rocket\":{\"id\":7},\"createdAt\":\"now\",") + "]");

        List<Launch> launches = apiClient(fakeApi.url()).fetchLaunches();

        assertEquals("Moon", launches.get(0).name());
    }

    @ParameterizedTest
    @CsvSource({"/, /activities", "/v1, /v1/activities", "/v1/, /v1/activities"})
    void should_call_the_configured_url_when_it_has_a_path_or_a_trailing_slash(String suffix, String expectedPath) {
        apiClient(fakeApi.url() + suffix).fetchLaunches();

        assertEquals(expectedPath, fakeApi.receivedPath());
    }

    @Test
    void should_fail_as_not_reachable_when_nothing_listens_on_the_port() {
        fakeApi.stop();

        ApiNotReachableFailure failure =
                assertThrows(ApiNotReachableFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());

        assertEquals(fakeApi.url(), failure.url());
    }

    @Test
    void should_fail_as_not_reachable_when_the_call_times_out() {
        fakeApi.blockResponses();
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), SHORT_TIMEOUT);

        ApiNotReachableFailure failure = assertThrows(ApiNotReachableFailure.class, apiClient::fetchLaunches);

        assertEquals(fakeApi.url(), failure.url());
    }

    @Test
    void should_keep_the_configured_text_when_the_url_has_a_trailing_slash_and_cannot_be_reached() {
        fakeApi.stop();
        String configuredUrl = fakeApi.url() + "/";

        ApiNotReachableFailure failure =
                assertThrows(ApiNotReachableFailure.class, () -> apiClient(configuredUrl).fetchLaunches());

        assertEquals(configuredUrl, failure.url());
    }

    @Test
    void should_restore_the_interrupt_flag_when_the_call_is_interrupted() {
        ApiClient apiClient = apiClient(fakeApi.url());
        Thread.currentThread().interrupt();

        assertThrows(ApiNotReachableFailure.class, apiClient::fetchLaunches);

        assertTrue(Thread.interrupted());
    }

    @Test
    void should_fail_as_unauthorized_when_the_api_responds_401() {
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");

        UnauthorizedFailure failure =
                assertThrows(UnauthorizedFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());

        assertEquals(new ApiError(401, "Unauthorized", List.of()), failure.apiError());
    }

    @Test
    void should_fail_as_unauthorized_with_a_generic_error_when_the_401_body_is_malformed() {
        fakeApi.respondWith(401, "<html>");

        UnauthorizedFailure failure =
                assertThrows(UnauthorizedFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());

        assertEquals(new ApiError(401, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_the_message_and_field_errors_when_the_api_responds_with_an_error() {
        fakeApi.respondWith(400, """
                {"message":"Invalid data","errors":[{"field":"email","message":"Already taken"}]}
                """);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(
                new ApiError(400, "Invalid data", List.of(new FieldError("email", "Already taken"))),
                failure.apiError());
    }

    @Test
    void should_hold_no_field_errors_when_the_error_has_no_errors() {
        fakeApi.respondWith(404, "{\"message\":\"Activity not found\"}");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(404, "Activity not found", List.of()), failure.apiError());
    }

    @Test
    void should_ignore_the_field_when_an_error_holds_an_unknown_one() {
        fakeApi.respondWith(500, """
                {"message":"Broken","code":7,"errors":[{"field":"name","message":"Bad","hint":"x"}]}
                """);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(500, "Broken", List.of(new FieldError("name", "Bad"))), failure.apiError());
    }

    @Test
    void should_fail_with_an_api_error_when_the_status_is_a_redirect() {
        fakeApi.respondWith(302, "");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(302, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_a_generic_error_when_the_body_is_not_valid_json() {
        fakeApi.respondWith(200, "<html>not json</html>");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_a_generic_error_when_the_body_is_json_null() {
        fakeApi.respondWith(200, "null");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_a_generic_error_when_an_activity_misses_a_field() {
        fakeApi.respondWith(200, "[{\"id\":\"activity-1\",\"name\":\"Moon\"}]");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name", "slug", "price", "date", "duration", "location", "minParticipants", "maxParticipants", "status"})
    void should_fail_with_a_generic_error_when_any_field_of_an_activity_is_missing(String field) {
        String activity = FakeApiServer.activityJson("Moon", "published");
        fakeApi.respondWith(200, "[" + activity.replace("\"" + field + "\":", "\"other\":") + "]");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(GENERIC_MESSAGE, failure.apiError().message());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "{}",
        "{\"id\":\"activity-1\"}",
        "\"text\"",
        "7",
        "[null]",
        "[7]",
        "[[]]",
        "[] trailing",
        "[{\"id\":\"a\",\"name\":\"n\",\"slug\":\"s\",\"price\":1,\"date\":\"d\",\"duration\":1.5,"
                + "\"location\":\"l\",\"minParticipants\":1,\"maxParticipants\":2,\"status\":\"published\"}]"
    })
    void should_fail_with_a_generic_error_when_the_ok_body_is_malformed(String body) {
        fakeApi.respondWith(200, body);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "<html>",
        "null",
        "[]",
        "{}",
        "{\"errors\":[]}",
        "{\"message\":null}",
        "{\"message\":\"Bad\",\"errors\":\"text\"}",
        "{\"message\":\"Bad\",\"errors\":{}}",
        "{\"message\":\"Bad\",\"errors\":[null]}",
        "{\"message\":\"Bad\",\"errors\":[7]}",
        "{\"message\":\"Bad\",\"errors\":[{\"field\":\"name\"}]}",
        "{\"message\":\"Bad\",\"errors\":[{\"message\":\"Bad\"}]}"
    })
    void should_fail_with_a_generic_error_when_the_error_body_is_malformed(String body) {
        fakeApi.respondWith(400, body);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(400, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_send_the_username_email_password_and_terms_when_registering() throws IOException {
        fakeApi.respondWith(201, AUTH_BODY);

        apiClient(fakeApi.url()).register("ana", "ana@example.com", " secret ");

        assertEquals("POST", fakeApi.receivedMethod());
        assertEquals("/users", fakeApi.receivedPath());
        assertEquals("application/json", fakeApi.receivedContentType());
        assertEquals(
                JSON.readTree("""
                        {"username":"ana","email":"ana@example.com","password":" secret ","terms":true}
                        """),
                JSON.readTree(fakeApi.receivedBodies().get(0)));
    }

    @Test
    void should_send_the_email_and_password_when_logging_in() throws IOException {
        fakeApi.respondWith(200, AUTH_BODY);

        apiClient(fakeApi.url()).login("ana@example.com", "secret");

        assertEquals("POST", fakeApi.receivedMethod());
        assertEquals("/login", fakeApi.receivedPath());
        assertEquals("application/json", fakeApi.receivedContentType());
        assertEquals(
                JSON.readTree("{\"email\":\"ana@example.com\",\"password\":\"secret\"}"),
                JSON.readTree(fakeApi.receivedBodies().get(0)));
    }

    @Test
    void should_return_the_session_when_registering_succeeds() {
        fakeApi.respondWith(201, AUTH_BODY);

        Session session = apiClient(fakeApi.url()).register("ana", "ana@example.com", "secret");

        assertEquals(new Session("token-1", "ana", "ana@example.com"), session);
    }

    @Test
    void should_return_the_session_when_logging_in_succeeds() {
        fakeApi.respondWith(200, AUTH_BODY);

        Session session = apiClient(fakeApi.url()).login("ana@example.com", "secret");

        assertEquals(new Session("token-1", "ana", "ana@example.com"), session);
    }

    @Test
    void should_ignore_the_other_user_fields_when_the_api_sends_them() {
        fakeApi.respondWith(201, """
                {"user":{"id":"user-1","username":"ana","email":"ana@example.com","terms":true},
                 "accessToken":"token-1","other":7}
                """);

        Session session = apiClient(fakeApi.url()).register("ana", "ana@example.com", "secret");

        assertEquals(new Session("token-1", "ana", "ana@example.com"), session);
    }

    @Test
    void should_fail_with_the_field_errors_when_the_email_is_repeated() {
        fakeApi.respondWith(400, """
                {"message":"Invalid data","errors":[{"field":"email","message":"Email is already registered"}]}
                """);
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiErrorFailure failure = assertThrows(
                ApiErrorFailure.class, () -> apiClient.register("ana", "ana@example.com", "secret"));

        assertEquals(
                new ApiError(400, "Invalid data", List.of(new FieldError("email", "Email is already registered"))),
                failure.apiError());
    }

    @Test
    void should_fail_as_unauthorized_when_login_responds_401() {
        fakeApi.respondWith(401, "{\"message\":\"Authentication failed\",\"errors\":[]}");
        ApiClient apiClient = apiClient(fakeApi.url());

        UnauthorizedFailure failure =
                assertThrows(UnauthorizedFailure.class, () -> apiClient.login("ana@example.com", "secret"));

        assertEquals(new ApiError(401, "Authentication failed", List.of()), failure.apiError());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "<html>",
        "null",
        "[]",
        "{}",
        "{\"accessToken\":\"token-1\"}",
        "{\"user\":null,\"accessToken\":\"token-1\"}",
        "{\"user\":\"ana\",\"accessToken\":\"token-1\"}",
        "{\"user\":{\"username\":\"ana\",\"email\":\"ana@example.com\"}}",
        "{\"user\":{\"username\":\"ana\",\"email\":\"ana@example.com\"},\"accessToken\":null}",
        "{\"user\":{\"username\":\"ana\",\"email\":\"ana@example.com\"},\"accessToken\":\"  \"}",
        "{\"user\":{\"email\":\"ana@example.com\"},\"accessToken\":\"token-1\"}",
        "{\"user\":{\"username\":\" \",\"email\":\"ana@example.com\"},\"accessToken\":\"token-1\"}",
        "{\"user\":{\"username\":\"ana\"},\"accessToken\":\"token-1\"}",
        "{\"user\":{\"username\":\"ana\",\"email\":\"\"},\"accessToken\":\"token-1\"}"
    })
    void should_fail_with_a_generic_error_when_the_auth_response_is_incomplete(String body) {
        fakeApi.respondWith(200, body);
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiErrorFailure registerFailure = assertThrows(
                ApiErrorFailure.class, () -> apiClient.register("ana", "ana@example.com", "secret"));
        ApiErrorFailure loginFailure =
                assertThrows(ApiErrorFailure.class, () -> apiClient.login("ana@example.com", "secret"));

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), registerFailure.apiError());
        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), loginFailure.apiError());
    }

    @Test
    void should_send_the_launch_id_as_activity_id_and_the_seats_as_participants_when_creating_a_booking()
            throws IOException {
        fakeApi.respondWith(201, BOOKING_BODY);

        createBooking();

        assertEquals("POST", fakeApi.receivedMethod());
        assertEquals("/bookings", fakeApi.receivedPath());
        assertEquals("application/json", fakeApi.receivedContentType());
        assertEquals(
                JSON.readTree("{\"activityId\":\"launch-1\",\"participants\":2}"),
                JSON.readTree(fakeApi.receivedBodies().get(0)));
    }

    @Test
    void should_send_the_token_as_a_bearer_header_when_creating_a_booking() {
        fakeApi.respondWith(201, BOOKING_BODY);

        createBooking();

        assertEquals("Bearer token-1", fakeApi.receivedAuthorization());
    }

    @Test
    void should_return_the_booking_with_no_launch_when_the_response_has_no_activity() {
        fakeApi.respondWith(201, BOOKING_BODY);

        Booking booking = createBooking();

        assertEquals(
                new Booking("booking-1", "launch-1", "user-1", 2, "2026-11-05T10:00:00.000Z", "payment-1", "paid", null),
                booking);
    }

    @Test
    void should_return_the_booking_with_no_payment_id_when_the_response_has_none() {
        fakeApi.respondWith(201, BOOKING_BODY.replace("\"paymentId\":\"payment-1\",", ""));

        assertNull(createBooking().paymentId());
    }

    @Test
    void should_return_the_launch_summary_when_the_response_has_an_activity() {
        fakeApi.respondWith(200, bookingBodyWithActivity(BOOKING_ACTIVITY));

        Booking booking = createBooking();

        assertEquals(
                new LaunchSummary("Moon", "moon", new BigDecimal("158.50"), "2026-12-20", new BigDecimal("90"), "Kourou", "published"),
                booking.launch());
    }

    @Test
    void should_keep_a_fractional_duration_when_the_response_has_an_activity() {
        fakeApi.respondWith(200, bookingBodyWithActivity(BOOKING_ACTIVITY.replace("\"duration\":90", "\"duration\":1.5")));

        assertEquals(new BigDecimal("1.5"), createBooking().launch().duration());
    }

    @Test
    void should_ignore_the_other_fields_when_the_booking_response_has_them() {
        fakeApi.respondWith(201, BOOKING_BODY.replace("{", "{\"totalPrice\":316,\"updatedAt\":{\"at\":\"now\"},"));

        assertEquals("booking-1", createBooking().id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "<html>",
        "null",
        "[]",
        "7",
        "{}",
        "{\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\" \",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\" \",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\" \",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":2,\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\" \",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\"now\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\" \"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":null,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":1.5,\"createdAt\":\"now\",\"paymentStatus\":\"paid\"}",
        "{\"id\":\"booking-1\",\"activityId\":\"launch-1\",\"userId\":\"user-1\",\"participants\":2,\"createdAt\":\"now\",\"paymentStatus\":\"paid\",\"activity\":7}"
    })
    void should_fail_with_a_generic_error_when_the_booking_response_is_malformed(String body) {
        fakeApi.respondWith(201, body);

        ApiErrorFailure failure = createBookingExpectingApiError();

        assertEquals(new ApiError(201, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "slug", "price", "date", "duration", "location", "status"})
    void should_fail_with_a_generic_error_when_any_field_of_the_booking_activity_is_missing(String field) {
        fakeApi.respondWith(200, bookingBodyWithActivity(BOOKING_ACTIVITY.replace("\"" + field + "\":", "\"other\":")));

        ApiErrorFailure failure = createBookingExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_keep_the_status_and_exact_message_when_the_capacity_is_exceeded() {
        fakeApi.respondWith(400, "{\"message\":\"Capacity exceeded. Available: 2, Requested: 3\",\"errors\":[]}");

        ApiErrorFailure failure = createBookingExpectingApiError();

        assertEquals(new ApiError(400, "Capacity exceeded. Available: 2, Requested: 3", List.of()), failure.apiError());
    }

    @Test
    void should_keep_the_status_and_exact_message_when_the_payment_is_rejected() {
        fakeApi.respondWith(402, "{\"message\":\"Payment could not be processed\",\"errors\":[]}");

        ApiErrorFailure failure = createBookingExpectingApiError();

        assertEquals(new ApiError(402, "Payment could not be processed", List.of()), failure.apiError());
    }

    @Test
    void should_fail_as_unauthorized_when_creating_a_booking_responds_401() {
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");

        UnauthorizedFailure failure = assertThrows(UnauthorizedFailure.class, this::createBooking);

        assertEquals(new ApiError(401, "Unauthorized", List.of()), failure.apiError());
    }

    @Test
    void should_fail_as_not_reachable_when_creating_a_booking_with_the_api_down() {
        fakeApi.stop();

        ApiNotReachableFailure failure = assertThrows(ApiNotReachableFailure.class, this::createBooking);

        assertEquals(fakeApi.url(), failure.url());
    }

    @Test
    void should_fail_with_a_generic_error_when_the_token_cannot_be_sent_in_a_header() {
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiErrorFailure failure = assertThrows(
                ApiErrorFailure.class, () -> apiClient.createBooking("token\n1", "launch-1", BigInteger.TWO));

        assertEquals(new ApiError(0, GENERIC_MESSAGE, List.of()), failure.apiError());
        assertEquals(0, fakeApi.receivedRequests());
    }

    @Test
    void should_send_a_get_with_the_token_as_a_bearer_header_when_fetching_the_bookings() {
        apiClient(fakeApi.url()).fetchBookings("token-1");

        assertEquals("GET", fakeApi.receivedMethod());
        assertEquals("/bookings", fakeApi.receivedPath());
        assertEquals("Bearer token-1", fakeApi.receivedAuthorization());
    }

    @Test
    void should_return_the_bookings_in_the_api_order_with_their_launch_summary_when_fetching_the_bookings() {
        fakeApi.respondWith(200, "[" + RETRIEVED_BOOKING + "," + RETRIEVED_BOOKING.replace("booking-1", "booking-2") + "]");
        LaunchSummary moon = new LaunchSummary(
                "Moon", "moon", new BigDecimal("158.50"), "2026-12-20", new BigDecimal("90"), "Kourou", "published");

        List<Booking> bookings = apiClient(fakeApi.url()).fetchBookings("token-1");

        assertEquals(
                List.of(
                        new Booking("booking-1", "launch-1", "user-1", 2, "2026-11-05T10:00:00.000Z", "payment-1", "paid", moon),
                        new Booking("booking-2", "launch-1", "user-1", 2, "2026-11-05T10:00:00.000Z", "payment-1", "paid", moon)),
                bookings);
    }

    @Test
    void should_return_no_bookings_when_the_list_is_empty() {
        fakeApi.respondWith(200, "[]");

        assertEquals(List.of(), apiClient(fakeApi.url()).fetchBookings("token-1"));
    }

    @Test
    void should_keep_a_payment_status_the_client_does_not_know_when_fetching_the_bookings() {
        fakeApi.respondWith(200, "[" + RETRIEVED_BOOKING.replace("\"paid\"", "\"Under Review\"") + "]");

        List<Booking> bookings = apiClient(fakeApi.url()).fetchBookings("token-1");

        assertEquals("Under Review", bookings.get(0).paymentStatus());
    }

    @Test
    void should_keep_a_missing_payment_id_as_null_when_fetching_the_bookings() {
        fakeApi.respondWith(200, "[" + RETRIEVED_BOOKING.replace("\"paymentId\":\"payment-1\",", "") + "]");

        List<Booking> bookings = apiClient(fakeApi.url()).fetchBookings("token-1");

        assertNull(bookings.get(0).paymentId());
    }

    @Test
    void should_keep_a_fractional_duration_when_fetching_the_bookings() {
        fakeApi.respondWith(200, "[" + RETRIEVED_BOOKING.replace("\"duration\":90", "\"duration\":1.5") + "]");

        List<Booking> bookings = apiClient(fakeApi.url()).fetchBookings("token-1");

        assertEquals(new BigDecimal("1.5"), bookings.get(0).launch().duration());
    }

    @ParameterizedTest
    @MethodSource("malformedBookingLists")
    void should_fail_with_a_generic_error_when_the_bookings_response_is_malformed(String body) {
        fakeApi.respondWith(200, body);
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiErrorFailure failure = assertThrows(ApiErrorFailure.class, () -> apiClient.fetchBookings("token-1"));

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_request_the_chosen_id_with_the_token_as_a_bearer_header_when_fetching_a_booking() {
        fakeApi.respondWith(200, RETRIEVED_BOOKING);

        apiClient(fakeApi.url()).fetchBooking("token-1", "booking-1");

        assertEquals("GET", fakeApi.receivedMethod());
        assertEquals("/bookings/booking-1", fakeApi.receivedPath());
        assertEquals("Bearer token-1", fakeApi.receivedAuthorization());
    }

    @Test
    void should_return_the_booking_with_its_launch_summary_when_fetching_a_booking() {
        fakeApi.respondWith(200, RETRIEVED_BOOKING);

        Booking booking = apiClient(fakeApi.url()).fetchBooking("token-1", "booking-1");

        assertEquals(
                new Booking(
                        "booking-1",
                        "launch-1",
                        "user-1",
                        2,
                        "2026-11-05T10:00:00.000Z",
                        "payment-1",
                        "paid",
                        new LaunchSummary("Moon", "moon", new BigDecimal("158.50"), "2026-12-20", new BigDecimal("90"), "Kourou", "published")),
                booking);
    }

    @Test
    void should_keep_a_fractional_duration_when_fetching_a_booking() {
        fakeApi.respondWith(200, RETRIEVED_BOOKING.replace("\"duration\":90", "\"duration\":1.5"));

        Booking booking = apiClient(fakeApi.url()).fetchBooking("token-1", "booking-1");

        assertEquals(new BigDecimal("1.5"), booking.launch().duration());
    }

    @ParameterizedTest
    @CsvSource({"a b, /bookings/a%20b", "a?b, /bookings/a%3Fb", "a/b, /bookings/a%2Fb"})
    void should_encode_the_id_as_one_path_segment_when_fetching_a_booking(String bookingId, String expectedRawPath) {
        fakeApi.respondWith(200, RETRIEVED_BOOKING);

        apiClient(fakeApi.url()).fetchBooking("token-1", bookingId);

        assertEquals(expectedRawPath, fakeApi.receivedRawPath());
    }

    @ParameterizedTest
    @MethodSource("malformedRetrievedBookings")
    void should_fail_with_a_generic_error_when_the_fetched_booking_response_is_malformed(String body) {
        fakeApi.respondWith(200, body);
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiErrorFailure failure =
                assertThrows(ApiErrorFailure.class, () -> apiClient.fetchBooking("token-1", "booking-1"));

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @ParameterizedTest
    @EnumSource(BookingRequest.class)
    void should_fail_as_unauthorized_when_the_booking_request_responds_401(BookingRequest request) {
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");
        ApiClient apiClient = apiClient(fakeApi.url());

        UnauthorizedFailure failure =
                assertThrows(UnauthorizedFailure.class, () -> request.sendWith(apiClient, "token-1"));

        assertEquals(new ApiError(401, "Unauthorized", List.of()), failure.apiError());
    }

    @ParameterizedTest
    @EnumSource(BookingRequest.class)
    void should_keep_the_status_and_message_when_the_booking_request_responds_with_an_error(BookingRequest request) {
        fakeApi.respondWith(404, "{\"message\":\"Booking not found\",\"errors\":[]}");
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiErrorFailure failure = assertThrows(ApiErrorFailure.class, () -> request.sendWith(apiClient, "token-1"));

        assertEquals(new ApiError(404, "Booking not found", List.of()), failure.apiError());
    }

    @ParameterizedTest
    @EnumSource(BookingRequest.class)
    void should_fail_as_not_reachable_when_the_booking_request_finds_the_api_down(BookingRequest request) {
        fakeApi.stop();
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiNotReachableFailure failure =
                assertThrows(ApiNotReachableFailure.class, () -> request.sendWith(apiClient, "token-1"));

        assertEquals(fakeApi.url(), failure.url());
    }

    @ParameterizedTest
    @EnumSource(BookingRequest.class)
    void should_fail_with_a_generic_error_when_the_token_of_the_booking_request_cannot_be_sent_in_a_header(
            BookingRequest request) {
        ApiClient apiClient = apiClient(fakeApi.url());

        ApiErrorFailure failure = assertThrows(ApiErrorFailure.class, () -> request.sendWith(apiClient, "token\n1"));

        assertEquals(new ApiError(0, GENERIC_MESSAGE, List.of()), failure.apiError());
        assertEquals(0, fakeApi.receivedRequests());
    }

    /** The two authenticated reads, so that a failure test runs for both. */
    enum BookingRequest {
        LIST_BOOKINGS {
            @Override
            void sendWith(ApiClient apiClient, String token) {
                apiClient.fetchBookings(token);
            }
        },
        ONE_BOOKING {
            @Override
            void sendWith(ApiClient apiClient, String token) {
                apiClient.fetchBooking(token, "booking-1");
            }
        };

        abstract void sendWith(ApiClient apiClient, String token);
    }

    static Stream<String> malformedBookingLists() {
        return Stream.of(
                "<html>",
                "null",
                "{}",
                RETRIEVED_BOOKING,
                "[null]",
                "[" + BOOKING_BODY + "]",
                "[" + RETRIEVED_BOOKING.replace("\"name\":", "\"other\":") + "]",
                "[" + RETRIEVED_BOOKING.replace("\"participants\":2", "\"participants\":1.5") + "]",
                "[" + RETRIEVED_BOOKING + ",null]");
    }

    static Stream<String> malformedRetrievedBookings() {
        return Stream.of(
                "<html>",
                "null",
                "[]",
                BOOKING_BODY,
                RETRIEVED_BOOKING.replace("\"name\":", "\"other\":"),
                RETRIEVED_BOOKING.replace("\"participants\":2", "\"participants\":1.5"));
    }

    private Booking createBooking() {
        return apiClient(fakeApi.url()).createBooking("token-1", "launch-1", BigInteger.TWO);
    }

    private ApiErrorFailure createBookingExpectingApiError() {
        return assertThrows(ApiErrorFailure.class, this::createBooking);
    }

    private static String bookingBodyWithActivity(String activity) {
        return BOOKING_BODY.replace("{", "{\"activity\":" + activity + ",");
    }

    private ApiErrorFailure fetchExpectingApiError() {
        return assertThrows(ApiErrorFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());
    }

    private ApiClient apiClient(String url) {
        return new ApiClient(HttpClient.newHttpClient(), url, TIMEOUT);
    }
}
