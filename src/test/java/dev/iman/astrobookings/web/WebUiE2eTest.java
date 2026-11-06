package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchService;
import dev.iman.astrobookings.service.SessionService;
import dev.iman.astrobookings.session.SessionStore;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance criteria of feat-web-ui, with real HTTP requests to the web server on port 0 and against the
 * real API (ADR-18). What the real API cannot reach is covered by unit tests, as the comment on each test says.
 *
 * <p>Each test creates its own launches at a price of 7 with whole values, because a fractional value makes
 * GET /activities fail for every later test, and checks only the bookings it creates. The part of AC11 that
 * needs a process, such as the exit code, is checked by running the jar.
 */
@Tag("e2e")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class WebUiE2eTest {

    private static final String VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final String PASSWORD = "E2e-Secret-Pass-1";
    private static final String TERMS_ACCEPTED = "accepted";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String LAUNCH_DATE = "2099-06-15T10:00:00.000Z";
    private static final String SHOWN_LAUNCH_DATE = "June 15, 2099, 10:00 UTC";
    private static final String LAUNCH_LOCATION = "E2E Pad";
    private static final int LAUNCH_PRICE = 7;
    private static final int LAUNCH_DURATION = 60;
    private static final int MIN_PARTICIPANTS = 1;
    private static final int MAX_PARTICIPANTS = 50;
    private static final Set<String> OPEN_STATUSES = Set.of("published", "confirmed");
    private static final List<String> CLOSED_STATUSES = List.of("draft", "sold-out", "done", "cancelled");
    private static final String NO_SESSION_LINE = "Register or log in first.";
    private static final String SESSION_REJECTED_LINE = "Your session is no longer valid. Please register again.";
    private static final String NO_BOOKINGS_LINE = "You have no bookings.";
    private static final String SEATS_ERROR_LINE = "The seats must be a whole number of at least 1.";
    private static final String NOT_BOOKABLE_LINE = "This launch is not open for booking.";
    private static final String TERMS_ERROR_LINE = "The terms were not accepted, so you were not registered.";
    private static final String EMAIL_TAKEN_LINE = "Email is already registered";
    private static final String LOGIN_BROKEN_LINE = "The login of the API is currently broken and rejects every attempt.";
    private static final String LOGIN_REJECTED_LINE =
            "The API did not accept the email or password, and its login is broken today.";
    private static final String LOGGED_IN_PREFIX = "Logged in as ";
    private static final String REGISTER_LINK = "href=\"/register\"";
    private static final String LOGIN_LINK = "href=\"/login\"";
    private static final String MARKUP = "<b>\"'";
    private static final String ESCAPED_MARKUP = "&lt;b&gt;&quot;&#39;";
    private static final Pattern OPTION = Pattern.compile("<option value=\"([^\"]*)\"[^>]*>([^<]*)</option>");
    private static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter
            .ofPattern("MMMM d, yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir
    Path temporaryFolder;

    private final List<WebServer> startedServers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        startedServers.forEach(WebServer::stop);
    }

    /** The empty message, which shared data cannot reach, is LaunchesPageTest.should_show_the_no_launches_message_when_none_qualifies. */
    @Test
    void AC1_should_show_every_field_of_the_open_launches_only_when_opening_the_launches_page() throws Exception {
        Customer customer = registerOnApi();
        CreatedLaunch published = createLaunch(customer, "published");
        CreatedLaunch confirmed = createLaunch(customer, "confirmed");
        List<CreatedLaunch> closed = new ArrayList<>();
        for (String status : CLOSED_STATUSES) {
            closed.add(createLaunch(customer, status));
        }
        Web web = startWeb(apiUrl(), noSessionFile());

        HttpResponse<String> page = web.get("/");

        assertEquals(200, page.statusCode());
        assertConsecutiveLines(page, launchLines(published));
        assertConsecutiveLines(page, launchLines(confirmed));
        for (CreatedLaunch launch : closed) {
            assertFalse(lines(page).contains("Name: " + launch.name()), launch.status() + " must not be shown");
        }
        List<JsonNode> activities = fetchActivities();
        List<String> openNames = activities.stream()
                .filter(activity -> OPEN_STATUSES.contains(activity.get("status").asText()))
                .map(activity -> "Name: " + activity.get("name").asText())
                .toList();
        assertEquals(openNames, lines(page).stream().filter(line -> line.startsWith("Name: ")).toList());
        assertFalse(ownTexts(page.body(), activities).contains("activit"), page::body);
        assertNoStackTrace(page);
    }

    /**
     * A successful login is SessionPagesTest.should_save_the_session_and_redirect_with_303_when_login_succeeds and
     * SessionPagesTest.should_show_who_is_logged_in_when_login_succeeds. The exact request body is
     * SessionPagesTest.should_send_the_fields_with_terms_true_when_registering.
     */
    @Test
    void AC2_should_save_the_session_and_redirect_with_303_when_registering_with_the_terms_accepted() throws Exception {
        String username = "e2e-user-" + COUNTER.incrementAndGet();
        String email = uniqueEmail();
        Path sessionFile = noSessionFile();
        Web web = startWeb(apiUrl(), sessionFile);

        HttpResponse<String> redirect =
                web.post("/register", "username", username, "email", email, "password", PASSWORD, "terms", TERMS_ACCEPTED);

        assertRedirectToRelativePath(redirect);
        assertTrue(Files.exists(sessionFile), "The session file must exist");
        String saved = Files.readString(sessionFile);
        JsonNode session = JSON.readTree(saved);
        assertEquals(username, session.get("username").asText());
        assertEquals(email, session.get("email").asText());
        assertFalse(saved.contains(PASSWORD), saved);
        assertFalse(saved.toLowerCase(Locale.ROOT).contains("password"), saved);
        assertTrue(fetchBookings(session.get("accessToken").asText()).isEmpty(), "The saved token must be accepted");

        HttpResponse<String> landing = web.follow(redirect);

        assertEquals(200, landing.statusCode());
        assertTrue(lines(landing).contains(LOGGED_IN_PREFIX + username + " (" + email + ")"), landing::body);
        assertFalse(landing.body().contains(PASSWORD), landing::body);

        // The session comes back from the file alone when the server starts again.
        HttpResponse<String> afterRestart = startWeb(apiUrl(), sessionFile).get("/");

        assertTrue(lines(afterRestart).contains(LOGGED_IN_PREFIX + username + " (" + email + ")"), afterRestart::body);
    }

    /** The absence of a request is SessionPagesTest.should_show_the_form_without_a_request_when_the_terms_are_not_accepted. */
    @Test
    void AC3_should_show_the_form_again_with_no_session_when_the_terms_are_missing_or_the_email_is_taken()
            throws Exception {
        // An email taken with a session running keeps the first user.
        String email = uniqueEmail();
        Path sessionFile = noSessionFile();
        Web web = startWeb(apiUrl(), sessionFile);
        assertRedirectToRelativePath(
                web.post("/register", "username", "first-user", "email", email, "password", PASSWORD, "terms", TERMS_ACCEPTED));
        String firstSession = Files.readString(sessionFile);

        HttpResponse<String> taken =
                web.post("/register", "username", "second-user", "email", email, "password", PASSWORD, "terms", TERMS_ACCEPTED);

        assertFormAgain(taken, EMAIL_TAKEN_LINE, "second-user", email);
        assertEquals(firstSession, Files.readString(sessionFile));
        assertTrue(lines(taken).contains(LOGGED_IN_PREFIX + "first-user (" + email + ")"), taken::body);

        // An email taken with no session starts none.
        Path otherFile = noSessionFile();
        HttpResponse<String> takenWithNoSession = startWeb(apiUrl(), otherFile)
                .post("/register", "username", "other-user", "email", email, "password", PASSWORD, "terms", TERMS_ACCEPTED);

        assertFormAgain(takenWithNoSession, EMAIL_TAKEN_LINE, "other-user", email);
        assertFalse(Files.exists(otherFile), "No session may start");
        assertFalse(takenWithNoSession.body().contains(LOGGED_IN_PREFIX), takenWithNoSession::body);

        // Terms not accepted: the email stays free, so nothing reached the API.
        String freeEmail = uniqueEmail();
        Path noTermsFile = noSessionFile();
        HttpResponse<String> noTerms = startWeb(apiUrl(), noTermsFile)
                .post("/register", "username", "no-terms-user", "email", freeEmail, "password", PASSWORD);

        assertFormAgain(noTerms, TERMS_ERROR_LINE, "no-terms-user", freeEmail);
        assertFalse(Files.exists(noTermsFile), "No session may start");
        assertFalse(noTerms.body().contains(LOGGED_IN_PREFIX), noTerms::body);
        assertEquals(201, postUser("no-terms-user", freeEmail).statusCode(), "The email must not have been registered");
    }

    @Test
    void AC4_should_explain_that_the_login_is_broken_and_link_to_registration_when_the_login_responds_401()
            throws Exception {
        Customer customer = registerOnApi();
        Path sessionFile = noSessionFile();
        Web web = startWeb(apiUrl(), sessionFile);

        HttpResponse<String> form = web.get("/login");

        assertEquals(200, form.statusCode());
        assertTrue(lines(form).contains(LOGIN_BROKEN_LINE), form::body);

        // The correct credentials of a real user, because the API rejects every attempt.
        HttpResponse<String> rejected = web.post("/login", "email", customer.email(), "password", PASSWORD);

        assertNotEquals(303, rejected.statusCode(), rejected::body);
        assertTrue(lines(rejected).contains(LOGIN_REJECTED_LINE), rejected::body);
        assertTrue(lines(rejected).contains(LOGIN_BROKEN_LINE), rejected::body);
        assertTrue(mainPart(rejected).contains(REGISTER_LINK), rejected::body);
        assertEquals(customer.email(), inputValue(rejected, "email"));
        assertEmptyPassword(rejected);
        assertFalse(Files.exists(sessionFile), "No session may start");
        assertNoStackTrace(rejected);
    }

    @Test
    void AC5_should_end_the_session_when_logging_out_or_when_the_api_rejects_the_saved_token() throws Exception {
        Customer customer = registerOnApi();
        CreatedLaunch launch = createLaunch(customer, "published");
        String bookingId = createBooking(customer, launch, 1);

        // A GET changes nothing.
        Path sessionFile = sessionFileOf(customer);
        Web web = startWeb(apiUrl(), sessionFile);
        assertEquals(405, web.get("/logout").statusCode());
        assertTrue(Files.exists(sessionFile), "A GET must not log out");
        assertTrue(web.get("/").body().contains(LOGGED_IN_PREFIX), "A GET must not log out");

        // Logging out, then again with no file.
        HttpResponse<String> loggedOut = web.post("/logout");

        assertRedirectToRelativePath(loggedOut);
        assertFalse(Files.exists(sessionFile), "The file must be gone");
        assertFalse(web.follow(loggedOut).body().contains(LOGGED_IN_PREFIX), "The session must have ended");

        HttpResponse<String> again = web.post("/logout");

        assertRedirectToRelativePath(again);
        HttpResponse<String> landing = web.follow(again);
        assertEquals(200, landing.statusCode());
        assertFalse(landing.body().contains("class=\"error\""), landing::body);

        // A rejected token, for each of the three requests.
        List<RejectedRequest> requests = List.of(
                new RejectedRequest("GET /bookings", server -> server.get("/bookings")),
                new RejectedRequest("GET /bookings/:id", server -> server.get("/bookings/" + bookingId)),
                new RejectedRequest(
                        "POST /bookings", server -> server.post("/book", "launchId", launch.id(), "seats", "1")));
        for (RejectedRequest request : requests) {
            Path rejectedFile = sessionFileWith("not-a-valid-token", "e2e-user", "e2e-saved@example.com");
            Web rejectedWeb = startWeb(apiUrl(), rejectedFile);

            HttpResponse<String> rejected = request.send().apply(rejectedWeb);

            assertNotEquals(303, rejected.statusCode(), request.name());
            assertTrue(lines(rejected).contains(SESSION_REJECTED_LINE), request.name() + ": " + rejected.body());
            assertTrue(mainPart(rejected).contains(REGISTER_LINK), request.name() + ": " + rejected.body());
            assertFalse(Files.exists(rejectedFile), request.name() + ": the file must be gone");
            assertFalse(rejected.body().contains(LOGGED_IN_PREFIX), request.name() + ": " + rejected.body());
            assertNoStackTrace(rejected);
            assertTrue(lines(rejectedWeb.get("/bookings")).contains(NO_SESSION_LINE), request.name());
        }
    }

    /**
     * The exact header and body are BookingPagesTest.should_send_the_launch_id_and_seats_with_the_bearer_token_when_booking.
     * The empty message is BookingPagesTest.should_show_the_no_launches_message_when_none_can_be_booked.
     */
    @Test
    void AC6_should_offer_the_open_launches_and_redirect_to_the_booking_when_booking_an_offered_launch()
            throws Exception {
        Customer customer = registerOnApi();
        Web web = startWeb(apiUrl(), sessionFileOf(customer));
        // Created after the server started, so only a fresh list can offer them.
        CreatedLaunch published = createLaunch(customer, "published");
        CreatedLaunch confirmed = createLaunch(customer, "confirmed");
        CreatedLaunch draft = createLaunch(customer, "draft");

        HttpResponse<String> form = web.get("/book");

        assertEquals(200, form.statusCode());
        Set<String> openIds = new HashSet<>();
        for (JsonNode activity : fetchActivities()) {
            if (OPEN_STATUSES.contains(activity.get("status").asText())) {
                openIds.add(activity.get("id").asText());
            }
        }
        Set<String> offered = optionValues(form);
        assertEquals(openIds, offered);
        assertTrue(offered.containsAll(List.of(published.id(), confirmed.id())), form::body);
        assertFalse(offered.contains(draft.id()), form::body);
        assertFalse(form.body().contains(draft.name()), form::body);
        assertTrue(form.body().contains("name=\"seats\""), form::body);

        for (CreatedLaunch launch : List.of(published, confirmed)) {
            HttpResponse<String> redirect = web.post("/book", "launchId", launch.id(), "seats", "2");

            assertRedirectToRelativePath(redirect);
            String location = redirect.headers().firstValue("Location").orElseThrow();
            String bookingId = location.substring(location.lastIndexOf('/') + 1);
            HttpResponse<String> detail = web.follow(redirect);

            assertEquals(200, detail.statusCode());
            assertTrue(lines(detail).contains("Booking id: " + bookingId), detail::body);
            assertTrue(lines(detail).contains("Launch: " + launch.name()), detail::body);
            assertTrue(lines(detail).contains("Seats: 2"), detail::body);
            assertTrue(lines(detail).contains("Payment status: paid"), detail::body);

            JsonNode booking = bookingWithId(fetchBookings(customer.token()), bookingId);
            assertEquals(launch.name(), booking.get("activity").get("name").asText());
            assertEquals(2, booking.get("participants").asInt());
            assertEquals("paid", booking.get("paymentStatus").asText());

            // A reload repeats the GET, and creates no second booking.
            int size = fetchBookings(customer.token()).size();
            assertEquals(200, web.follow(redirect).statusCode());
            assertEquals(size, fetchBookings(customer.token()).size());
        }
        assertEquals(2, fetchBookings(customer.token()).size());
    }

    /**
     * The absence of a request is BookingPagesTest.should_show_an_error_with_no_booking_request_when_there_is_no_session,
     * should_show_an_error_with_no_booking_request_when_the_seats_are_invalid and
     * should_show_an_error_with_no_booking_request_when_the_launch_is_not_in_the_fresh_list.
     */
    @Test
    void AC7_should_show_an_error_with_no_booking_when_the_session_the_seats_or_the_launch_are_not_valid()
            throws Exception {
        Customer customer = registerOnApi();
        CreatedLaunch published = createLaunch(customer, "published");
        CreatedLaunch draft = createLaunch(customer, "draft");
        List<JsonNode> before = fetchBookings(customer.token());
        Web web = startWeb(apiUrl(), sessionFileOf(customer));

        HttpResponse<String> noSession =
                startWeb(apiUrl(), noSessionFile()).post("/book", "launchId", published.id(), "seats", "1");

        assertNotEquals(303, noSession.statusCode(), noSession::body);
        assertTrue(lines(noSession).contains(NO_SESSION_LINE), noSession::body);
        assertNoStackTrace(noSession);

        for (String seats : List.of("0", "1.5", "text", "-1", "")) {
            HttpResponse<String> invalid = web.post("/book", "launchId", published.id(), "seats", seats);

            assertNotEquals(303, invalid.statusCode(), seats);
            assertTrue(lines(invalid).contains(SEATS_ERROR_LINE), seats + ": " + invalid.body());
            assertEquals(seats, inputValue(invalid, "seats"));
            assertNoStackTrace(invalid);
        }

        for (String launchId : List.of("forged-" + System.currentTimeMillis(), draft.id())) {
            HttpResponse<String> notOffered = web.post("/book", "launchId", launchId, "seats", "1");

            assertNotEquals(303, notOffered.statusCode(), launchId);
            assertTrue(lines(notOffered).contains(NOT_BOOKABLE_LINE), launchId + ": " + notOffered.body());
            assertNoStackTrace(notOffered);
        }

        assertEquals(before, fetchBookings(customer.token()));
    }

    /**
     * The real API also reaches the 402, with a total of 1000, which is what this test means to do. The unit test of
     * the plan is BookingPagesTest.should_show_the_message_and_that_no_booking_was_created_when_the_api_responds_402.
     */
    @Test
    void AC8_should_show_the_form_again_with_the_message_when_the_api_responds_400_for_capacity_or_402()
            throws Exception {
        Customer customer = registerOnApi();
        CreatedLaunch launch = createLaunch(customer, "published");
        createBooking(customer, launch, 3);
        List<JsonNode> before = fetchBookings(customer.token());
        Web web = startWeb(apiUrl(), sessionFileOf(customer));

        HttpResponse<String> capacity = web.post("/book", "launchId", launch.id(), "seats", "51");

        assertNotEquals(303, capacity.statusCode(), capacity::body);
        assertTrue(lines(capacity).contains("Capacity exceeded. Available: 47, Requested: 51"), capacity::body);
        assertTrue(capacity.body().contains("action=\"/book\""), capacity::body);
        assertEquals("51", inputValue(capacity, "seats"));
        assertTrue(capacity.body().contains("<option value=\"" + launch.id() + "\" selected>"), capacity::body);
        assertFalse(lines(capacity).contains("No booking was created."), capacity::body);
        assertNoStackTrace(capacity);
        assertEquals(before, fetchBookings(customer.token()));

        CreatedLaunch rejectedByPayment = createLaunch(customer, "published", 500, LAUNCH_LOCATION);

        HttpResponse<String> payment = web.post("/book", "launchId", rejectedByPayment.id(), "seats", "2");

        assertNotEquals(303, payment.statusCode(), payment::body);
        assertConsecutiveLines(payment, List.of("Payment could not be processed", "No booking was created."));
        assertTrue(payment.body().contains("action=\"/book\""), payment::body);
        assertNoStackTrace(payment);
        assertEquals(before, fetchBookings(customer.token()));
    }

    /**
     * The exact header and the absence of a request are
     * BookingRetrievalPagesTest.should_request_the_bookings_with_the_bearer_token_when_opening_my_bookings,
     * should_request_the_id_with_the_bearer_token_when_opening_a_detail and
     * should_tell_the_user_to_register_or_log_in_with_no_request_when_opening_either_page_with_no_session.
     */
    @Test
    void AC9_should_show_the_bookings_and_the_detail_as_the_api_sends_them_when_opening_each_page() throws Exception {
        Customer customer = registerOnApi();
        CreatedLaunch firstLaunch = createLaunch(customer, "published");
        CreatedLaunch secondLaunch = createLaunch(customer, "confirmed");
        String first = createBooking(customer, firstLaunch, 1);
        String second = createBooking(customer, secondLaunch, 3);
        Web web = startWeb(apiUrl(), sessionFileOf(customer));

        HttpResponse<String> list = web.get("/bookings");

        assertEquals(200, list.statusCode());
        List<JsonNode> bookings = fetchBookings(customer.token());
        assertEquals(bookings.size(), lines(list).stream().filter(line -> line.startsWith("Booking id: ")).count());
        for (JsonNode booking : bookings) {
            String id = booking.get("id").asText();
            assertConsecutiveLines(list, List.of(
                    "Booking id: " + id,
                    "Launch: " + booking.get("activity").get("name").asText(),
                    "Seats: " + booking.get("participants").asInt(),
                    "Payment status: " + booking.get("paymentStatus").asText()));
            assertTrue(list.body().contains("href=\"/bookings/" + id + "\""), list::body);
        }
        assertTrue(lines(list).contains("Booking id: " + first), list::body);
        assertTrue(lines(list).contains("Booking id: " + second), list::body);

        HttpResponse<String> detail = web.get("/bookings/" + second);

        assertEquals(200, detail.statusCode());
        JsonNode expected = fetchBooking(customer.token(), second);
        JsonNode launch = expected.get("activity");
        assertFalse(expected.get("paymentId").asText().isBlank(), expected::toString);
        assertEquals(secondLaunch.name(), launch.get("name").asText());
        assertConsecutiveLines(detail, List.of(
                "Booking id: " + second,
                "Seats: 3",
                "Created: " + INSTANT_FORMAT.format(Instant.parse(expected.get("createdAt").asText())),
                "Payment id: " + expected.get("paymentId").asText(),
                "Payment status: " + expected.get("paymentStatus").asText(),
                "Launch: " + secondLaunch.name(),
                "Price: " + LAUNCH_PRICE,
                "Date: " + SHOWN_LAUNCH_DATE,
                "Duration: " + LAUNCH_DURATION,
                "Location: " + LAUNCH_LOCATION,
                "Launch status: " + launch.get("status").asText()));
        assertFalse(lines(detail).contains("Booking id: " + first), detail::body);

        // A new customer.
        Customer newCustomer = registerOnApi();
        assertTrue(
                fetchBookings(newCustomer.token()).isEmpty(),
                "A new customer holds bookings because ids were reused: restore the API's data as ADR-5 says");

        HttpResponse<String> empty = startWeb(apiUrl(), sessionFileOf(newCustomer)).get("/bookings");

        assertTrue(lines(empty).contains(NO_BOOKINGS_LINE), empty::body);

        // No session, on either page.
        Web noSession = startWeb(apiUrl(), noSessionFile());
        for (String path : List.of("/bookings", "/bookings/" + second)) {
            HttpResponse<String> page = noSession.get(path);

            assertTrue(lines(page).contains(NO_SESSION_LINE), path + ": " + page.body());
            assertTrue(mainPart(page).contains(REGISTER_LINK), path + ": " + page.body());
            assertTrue(mainPart(page).contains(LOGIN_LINK), path + ": " + page.body());
            assertFalse(page.body().contains(secondLaunch.name()), path + ": " + page.body());
            assertNoStackTrace(page);
        }
    }

    /**
     * The timeout is LaunchesPageTest.should_show_not_reachable_when_the_api_call_times_out. Logging out and the forms
     * shown with GET call no API, so they are checked only for answering with no stack trace.
     */
    @Test
    void AC10_should_show_not_reachable_or_the_api_error_with_no_stack_trace_when_a_call_fails() throws Exception {
        String url = urlWithNothingListening();
        String notReachableLine = "API not reachable at " + url;
        Path sessionFile = sessionFileWith("any-token", "e2e-user", "e2e-saved@example.com");
        String savedSession = Files.readString(sessionFile);
        Web withSession = startWeb(url, sessionFile);
        Web noSession = startWeb(url, noSessionFile());

        Map<String, HttpResponse<String>> answers = new java.util.LinkedHashMap<>();
        answers.put("GET / with a session", withSession.get("/"));
        answers.put("GET /book", withSession.get("/book"));
        answers.put("GET /bookings", withSession.get("/bookings"));
        answers.put("GET /bookings/:id", withSession.get("/bookings/any-id"));
        answers.put("POST /book", withSession.post("/book", "launchId", "any-id", "seats", "1"));
        answers.put(
                "POST /register with a session",
                withSession.post("/register", "username", "u", "email", uniqueEmail(), "password", PASSWORD, "terms", TERMS_ACCEPTED));
        answers.put("POST /login with a session", withSession.post("/login", "email", uniqueEmail(), "password", PASSWORD));
        answers.put("GET / with no session", noSession.get("/"));
        answers.put(
                "POST /register with no session",
                noSession.post("/register", "username", "u", "email", uniqueEmail(), "password", PASSWORD, "terms", TERMS_ACCEPTED));
        answers.put("POST /login with no session", noSession.post("/login", "email", uniqueEmail(), "password", PASSWORD));

        answers.forEach((name, answer) -> {
            assertNotEquals(303, answer.statusCode(), name);
            assertTrue(lines(answer).contains(notReachableLine), name + ": " + answer.body());
            assertNoStackTrace(answer);
        });
        assertEquals(savedSession, Files.readString(sessionFile));

        for (String path : List.of("/register", "/login", "/style.css")) {
            HttpResponse<String> answer = withSession.get(path);
            assertEquals(200, answer.statusCode(), path);
            assertNoStackTrace(answer);
        }
        assertRedirectToRelativePath(withSession.post("/logout"));

        // Against the real API: the message, then the field and message of each field error.
        Web real = startWeb(apiUrl(), noSessionFile());
        Customer customer = registerOnApi();

        HttpResponse<String> repeated = real.post(
                "/register", "username", "u", "email", customer.email(), "password", PASSWORD, "terms", TERMS_ACCEPTED);

        assertConsecutiveLines(repeated, List.of(EMAIL_TAKEN_LINE, "email: " + EMAIL_TAKEN_LINE));
        assertNoStackTrace(repeated);

        HttpResponse<String> blank = real.post("/register", "username", "", "email", "", "password", "", "terms", TERMS_ACCEPTED);

        HttpResponse<String> blankOnApi = postJson("/users", null, JSON.writeValueAsString(
                Map.of("username", "", "email", "", "password", "", "terms", true)));
        assertEquals(400, blankOnApi.statusCode(), blankOnApi::body);
        assertConsecutiveLines(blank, expectedErrorLines(JSON.readTree(blankOnApi.body())));
        assertNoStackTrace(blank);

        String unknownId = "unknown-" + System.currentTimeMillis();
        HttpResponse<String> unknownOnApi = send(authorized("/bookings/" + unknownId, customer.token()).GET().build());
        assertTrue(unknownOnApi.statusCode() >= 400, unknownOnApi::body);

        HttpResponse<String> unknown = startWeb(apiUrl(), sessionFileOf(customer)).get("/bookings/" + unknownId);

        assertConsecutiveLines(unknown, expectedErrorLines(JSON.readTree(unknownOnApi.body())));
        assertNoStackTrace(unknown);
    }

    /**
     * The rest of the criterion needs a process, and is checked by running the jar: the address printed by --web, the
     * invalid and busy ports with exit code 1, and the console with no --web. The default port is
     * MainTest.should_use_port_8080_when_the_web_port_is_unset_or_blank.
     */
    @Test
    void AC11_should_serve_only_on_127_0_0_1_when_started() throws Exception {
        Web web = startWeb(apiUrl(), noSessionFile());
        int port = web.server().port();

        assertEquals(200, web.get("/style.css").statusCode());

        List<String> otherAddresses = new ArrayList<>(List.of("127.0.0.2"));
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (network.isUp() && !network.isLoopback()) {
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
                        otherAddresses.add(address.getHostAddress());
                    }
                }
            }
        }
        for (String address : otherAddresses) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + address + ":" + port + "/style.css"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            assertThrows(
                    IOException.class,
                    () -> HTTP.send(request, HttpResponse.BodyHandlers.ofString()),
                    "Must not be served on " + address);
        }
    }

    /**
     * The absence of an API call is WebServerTest.should_answer_403_without_calling_the_api_when_a_post_has_a_foreign_origin.
     * The launch id inside the option's attribute is BookingPagesTest.should_escape_the_typed_seats_and_the_launch_id_when_showing_the_form
     * and WebRulesTest.should_escape_markup_from_the_api_in_every_page, because the real API gives ids with no markup.
     */
    @Test
    void AC12_should_escape_all_text_and_refuse_a_foreign_origin_when_serving_every_page() throws Exception {
        Customer customer = registerOnApi();
        String stamp = System.currentTimeMillis() + "-" + COUNTER.incrementAndGet();
        String name = "E2E " + MARKUP + " " + stamp;
        String escapedName = "E2E " + ESCAPED_MARKUP + " " + stamp;
        String location = "Pad " + MARKUP;
        CreatedLaunch launch = createLaunch(customer, "published", LAUNCH_PRICE, location, name);
        Path sessionFile = sessionFileWith(customer.token(), "user " + MARKUP, "mail" + MARKUP + "@example.com");
        Web web = startWeb(apiUrl(), sessionFile);

        // Text from the API, and the session's own text, on every page.
        HttpResponse<String> launches = web.get("/");
        assertEscaped(launches);
        assertTrue(launches.body().contains("Name: " + escapedName), launches::body);
        assertTrue(launches.body().contains("Location: Pad " + ESCAPED_MARKUP), launches::body);
        assertTrue(lines(launches).contains("Name: " + name), "It must read as plain text");
        assertTrue(lines(launches).contains(
                LOGGED_IN_PREFIX + "user " + MARKUP + " (mail" + MARKUP + "@example.com)"), launches::body);

        HttpResponse<String> form = web.get("/book");
        assertEscaped(form);
        assertTrue(form.body().contains(escapedName), form::body);

        HttpResponse<String> redirect = web.post("/book", "launchId", launch.id(), "seats", "1");
        assertRedirectToRelativePath(redirect);
        HttpResponse<String> detail = web.follow(redirect);
        assertEscaped(detail);
        assertTrue(detail.body().contains("Launch: " + escapedName), detail::body);
        assertTrue(detail.body().contains("Location: Pad " + ESCAPED_MARKUP), detail::body);

        HttpResponse<String> list = web.get("/bookings");
        assertEscaped(list);
        assertTrue(list.body().contains("Launch: " + escapedName), list::body);

        // Typed text, inside the attributes of the forms shown again.
        String typed = "x\"><script>alert(1)</script>'@example.com";
        String escapedTyped = "x&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;&#39;@example.com";
        Web noSession = startWeb(apiUrl(), noSessionFile());

        HttpResponse<String> registration = noSession.post("/register", "username", typed, "email", typed, "password", PASSWORD);
        assertFalse(registration.body().contains("<script"), registration::body);
        assertTrue(registration.body().contains("name=\"email\" value=\"" + escapedTyped + "\""), registration::body);
        assertTrue(registration.body().contains("name=\"username\" value=\"" + escapedTyped + "\""), registration::body);

        HttpResponse<String> login = noSession.post("/login", "email", typed, "password", PASSWORD);
        assertFalse(login.body().contains("<script"), login::body);
        assertTrue(login.body().contains("name=\"email\" value=\"" + escapedTyped + "\""), login::body);

        HttpResponse<String> seats = web.post("/book", "launchId", typed, "seats", typed);
        assertFalse(seats.body().contains("<script"), seats::body);
        assertTrue(seats.body().contains("name=\"seats\" value=\"" + escapedTyped + "\""), seats::body);

        // A foreign origin changes nothing, on any action.
        int port = web.server().port();
        String savedSession = Files.readString(sessionFile);
        List<JsonNode> before = fetchBookings(customer.token());
        String freeEmail = uniqueEmail();
        for (String origin : List.of(
                "https://evil.example",
                "http://127.0.0.1:1",
                "null",
                "https://127.0.0.1:" + port,
                "http://evil.example:" + port)) {
            assertEquals(403, web.postFrom(origin, "/book", "launchId", launch.id(), "seats", "1").statusCode(), origin);
            assertEquals(403, web.postFrom(origin, "/logout").statusCode(), origin);
            assertEquals(
                    403,
                    web.postFrom(origin, "/register", "username", "u", "email", freeEmail, "password", PASSWORD, "terms", TERMS_ACCEPTED)
                            .statusCode(),
                    origin);
            assertEquals(403, web.postFrom(origin, "/login", "email", freeEmail, "password", PASSWORD).statusCode(), origin);
        }
        assertEquals(before, fetchBookings(customer.token()));
        assertEquals(savedSession, Files.readString(sessionFile));
        assertTrue(web.get("/").body().contains(LOGGED_IN_PREFIX), "The session must stay");
        assertEquals(201, postUser("u", freeEmail).statusCode(), "The foreign registration must not have reached the API");

        // Its own origins, with the bound port, and no origin at all.
        assertRedirectToRelativePath(
                web.postFrom("http://127.0.0.1:" + port, "/book", "launchId", launch.id(), "seats", "1"));
        assertRedirectToRelativePath(
                web.postFrom("http://localhost:" + port, "/book", "launchId", launch.id(), "seats", "1"));
        assertRedirectToRelativePath(web.post("/book", "launchId", launch.id(), "seats", "1"));
        assertEquals(before.size() + 3, fetchBookings(customer.token()).size());
    }

    private Web startWeb(String url, Path sessionFile) {
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), url, REQUEST_TIMEOUT);
        LaunchService launchService = new LaunchService(apiClient);
        SessionService sessionService = new SessionService(apiClient, new SessionStore(sessionFile));
        BookingService bookingService = new BookingService(apiClient, launchService, sessionService);
        WebServer server = WebServer.start(launchService, sessionService, bookingService, 0);
        startedServers.add(server);
        return new Web(server);
    }

    private Path noSessionFile() {
        return temporaryFolder.resolve("session-" + COUNTER.incrementAndGet() + ".json");
    }

    private Path sessionFileOf(Customer customer) throws IOException {
        return sessionFileWith(customer.token(), customer.username(), customer.email());
    }

    private Path sessionFileWith(String token, String username, String email) throws IOException {
        Path file = noSessionFile();
        Files.writeString(file, JSON.writeValueAsString(Map.of("accessToken", token, "username", username, "email", email)));
        return file;
    }

    private static String uniqueEmail() {
        return "e2e-" + System.currentTimeMillis() + "-" + COUNTER.incrementAndGet() + "@example.com";
    }

    private static Customer registerOnApi() throws IOException, InterruptedException {
        String username = "e2e-user-" + COUNTER.incrementAndGet();
        String email = uniqueEmail();
        HttpResponse<String> response = postUser(username, email);
        assertEquals(201, response.statusCode(), response::body);
        return new Customer(username, email, JSON.readTree(response.body()).get("accessToken").asText());
    }

    private static HttpResponse<String> postUser(String username, String email) throws IOException, InterruptedException {
        return postJson("/users", null, JSON.writeValueAsString(
                Map.of("username", username, "email", email, "password", PASSWORD, "terms", true)));
    }

    private static CreatedLaunch createLaunch(Customer customer, String status) throws IOException, InterruptedException {
        return createLaunch(customer, status, LAUNCH_PRICE, LAUNCH_LOCATION);
    }

    private static CreatedLaunch createLaunch(Customer customer, String status, int price, String location)
            throws IOException, InterruptedException {
        String name = "E2E Web Launch " + System.currentTimeMillis() + "-" + COUNTER.incrementAndGet();
        return createLaunch(customer, status, price, location, name);
    }

    private static CreatedLaunch createLaunch(Customer customer, String status, int price, String location, String name)
            throws IOException, InterruptedException {
        String body = JSON.writeValueAsString(Map.of(
                "name", name,
                "price", price,
                "date", LAUNCH_DATE,
                "duration", LAUNCH_DURATION,
                "location", location,
                "minParticipants", MIN_PARTICIPANTS,
                "maxParticipants", MAX_PARTICIPANTS,
                "status", status));
        HttpResponse<String> response = postJson("/activities", customer.token(), body);
        assertEquals(201, response.statusCode(), response::body);
        return new CreatedLaunch(JSON.readTree(response.body()).get("id").asText(), name, status);
    }

    private static String createBooking(Customer customer, CreatedLaunch launch, int seats)
            throws IOException, InterruptedException {
        String body = JSON.writeValueAsString(Map.of("activityId", launch.id(), "participants", seats));
        HttpResponse<String> response = postJson("/bookings", customer.token(), body);
        assertEquals(201, response.statusCode(), response::body);
        return JSON.readTree(response.body()).get("id").asText();
    }

    private static List<JsonNode> fetchActivities() throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(apiUrl() + "/activities"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build());
        assertEquals(200, response.statusCode(), response::body);
        return items(JSON.readTree(response.body()));
    }

    private static List<JsonNode> fetchBookings(String token) throws IOException, InterruptedException {
        HttpResponse<String> response = send(authorized("/bookings", token).GET().build());
        assertEquals(200, response.statusCode(), response::body);
        return items(JSON.readTree(response.body()));
    }

    private static JsonNode fetchBooking(String token, String id) throws IOException, InterruptedException {
        HttpResponse<String> response = send(authorized("/bookings/" + id, token).GET().build());
        assertEquals(200, response.statusCode(), response::body);
        return JSON.readTree(response.body());
    }

    private static JsonNode bookingWithId(List<JsonNode> bookings, String id) {
        return bookings.stream()
                .filter(booking -> booking.get("id").asText().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Booking " + id + " is not in GET /bookings: " + bookings));
    }

    private static List<JsonNode> items(JsonNode array) {
        assertTrue(array.isArray(), array::toString);
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    private static HttpResponse<String> postJson(String path, String token, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = token == null
                ? HttpRequest.newBuilder(URI.create(apiUrl() + path)).timeout(REQUEST_TIMEOUT)
                : authorized(path, token);
        return send(request
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private static HttpRequest.Builder authorized(String path, String token) {
        return HttpRequest.newBuilder(URI.create(apiUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token);
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static List<String> launchLines(CreatedLaunch launch) {
        return List.of(
                "Name: " + launch.name(),
                "Price: " + LAUNCH_PRICE,
                "Date: " + SHOWN_LAUNCH_DATE,
                "Duration: " + LAUNCH_DURATION,
                "Location: " + LAUNCH_LOCATION,
                "Minimum participants: " + MIN_PARTICIPANTS,
                "Maximum participants: " + MAX_PARTICIPANTS,
                "Status: " + launch.status());
    }

    private static List<String> expectedErrorLines(JsonNode error) {
        List<String> expected = new ArrayList<>(List.of(error.get("message").asText()));
        JsonNode fieldErrors = error.get("errors");
        if (fieldErrors != null) {
            fieldErrors.forEach(fieldError ->
                    expected.add(fieldError.get("field").asText() + ": " + fieldError.get("message").asText()));
        }
        return expected;
    }

    /** The page's text in lower case, with the API's own values taken out, because those are not the client's texts. */
    private static String ownTexts(String body, List<JsonNode> activities) {
        String text = body;
        for (JsonNode activity : activities) {
            text = text.replace(HtmlEscaper.escape(activity.get("name").asText()), "");
            text = text.replace(HtmlEscaper.escape(activity.get("location").asText()), "");
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /** The visible text, one line per element, with the entities read back. A link stays inside its line. */
    private static List<String> lines(HttpResponse<String> page) {
        String withoutLinks = page.body().replaceAll("</?a\\b[^>]*>", "");
        return Arrays.stream(withoutLinks.replaceAll("<[^>]+>", "\n").split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(WebUiE2eTest::unescape)
                .toList();
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    /** What follows the navigation, whose links are on every page, so that a link found here is the page's own. */
    private static String mainPart(HttpResponse<String> page) {
        return page.body().substring(page.body().indexOf("<main>"));
    }

    private static Set<String> optionValues(HttpResponse<String> page) {
        Set<String> values = new HashSet<>();
        Matcher matcher = OPTION.matcher(page.body());
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static String inputValue(HttpResponse<String> page, String name) {
        Matcher input = Pattern.compile("<input\\b[^>]*\\bname=\"" + name + "\"[^>]*>").matcher(page.body());
        assertTrue(input.find(), () -> "No field " + name + " in " + page.body());
        Matcher value = Pattern.compile("\\bvalue=\"([^\"]*)\"").matcher(input.group());
        return value.find() ? unescape(value.group(1)) : "";
    }

    private static void assertFormAgain(
            HttpResponse<String> page, String expectedError, String typedUsername, String typedEmail) {
        assertNotEquals(303, page.statusCode(), page::body);
        assertTrue(lines(page).contains(expectedError), page::body);
        assertEquals(typedUsername, inputValue(page, "username"));
        assertEquals(typedEmail, inputValue(page, "email"));
        assertEmptyPassword(page);
        assertNoStackTrace(page);
    }

    private static void assertEmptyPassword(HttpResponse<String> page) {
        assertEquals("", inputValue(page, "password"), page::body);
        assertFalse(page.body().contains(PASSWORD), page::body);
    }

    private static void assertRedirectToRelativePath(HttpResponse<String> response) {
        assertEquals(303, response.statusCode(), response::body);
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith("/") && !location.startsWith("//"), location);
    }

    private static void assertConsecutiveLines(HttpResponse<String> page, List<String> expected) {
        assertTrue(
                Collections.indexOfSubList(lines(page), expected) >= 0,
                () -> "Expected the consecutive lines " + expected + " in " + lines(page));
    }

    private static void assertEscaped(HttpResponse<String> page) {
        assertFalse(page.body().contains("<b>"), page::body);
        assertFalse(page.body().contains(MARKUP), page::body);
        assertTrue(page.body().contains(ESCAPED_MARKUP), page::body);
    }

    private static void assertNoStackTrace(HttpResponse<String> page) {
        assertFalse(page.body().contains("Exception"), page::body);
        assertFalse(page.body().contains("Caused by"), page::body);
        assertFalse(page.body().contains("\tat "), page::body);
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

    @FunctionalInterface
    private interface WebRequest {
        HttpResponse<String> apply(Web web) throws IOException, InterruptedException;
    }

    private record RejectedRequest(String name, WebRequest send) {
    }

    private record Customer(String username, String email, String token) {
    }

    private record CreatedLaunch(String id, String name, String status) {
    }

    /** A browser that does not follow redirects, so that a test can check the 303 and its Location. */
    private record Web(WebServer server) {

        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            return send(requestTo(path).GET().build());
        }

        HttpResponse<String> post(String path, String... fields) throws IOException, InterruptedException {
            return send(formTo(path, fields).build());
        }

        HttpResponse<String> postFrom(String origin, String path, String... fields)
                throws IOException, InterruptedException {
            return send(formTo(path, fields).header("Origin", origin).build());
        }

        HttpResponse<String> follow(HttpResponse<String> redirect) throws IOException, InterruptedException {
            return get(redirect.headers().firstValue("Location").orElseThrow());
        }

        private HttpRequest.Builder formTo(String path, String... fields) {
            List<String> pairs = new ArrayList<>();
            for (int index = 0; index < fields.length; index += 2) {
                pairs.add(encode(fields[index]) + "=" + encode(fields[index + 1]));
            }
            return requestTo(path)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)));
        }

        private HttpRequest.Builder requestTo(String path) {
            return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                    .timeout(REQUEST_TIMEOUT);
        }

        private static String encode(String text) {
            return URLEncoder.encode(text, StandardCharsets.UTF_8);
        }
    }
}
