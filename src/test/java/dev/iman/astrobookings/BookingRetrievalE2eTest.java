package dev.iman.astrobookings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance criteria of feat-booking-retrieval, against the real API. What the real API cannot reach is
 * covered by ConsoleMenuTest and ApiClientTest, as the comment on each test says.
 *
 * <p>Each test creates its own launches at a price of 7, so that no total is a multiple of 1000, and with
 * whole values, because a fractional one makes GET /activities fail for every later test. A booking's number
 * comes from GET /bookings, because ids reused after an API restart can show a new user old bookings (ADR-5).
 *
 * <p>The AC8 test leaves a booking whose launch is gone, so the API's data must be restored as ADR-5 says
 * before the API is restarted.
 */
@Tag("e2e")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class BookingRetrievalE2eTest {

    private static final String VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final String USERNAME = "e2e-user";
    private static final String PASSWORD = "E2e-Secret-Pass-1";
    private static final String LIST = "6\n";
    private static final String VIEW = "7\n";
    private static final String CANCEL = "0\n";
    private static final String EXIT = "0\n";
    private static final String BOOKING_PROMPT = "Booking number (0 to cancel):";
    private static final String INVALID_NUMBER_LINE = "Enter a booking number from the list.";
    private static final String NO_BOOKINGS_LINE = "You have no bookings.";
    private static final String NO_SESSION_LINE = "Register or log in first.";
    private static final String SESSION_REJECTED_LINE = "Your session is no longer valid. Please register again.";
    private static final String GENERIC_ERROR_LINE = "The API returned an unexpected response.";
    private static final String LIST_FAILED_LINE = "Failed to retrieve bookings";
    private static final String VIEW_FAILED_LINE = "Failed to retrieve booking";
    private static final String DETAILS_PREFIX = "Booking id: ";
    private static final String MENU_LINE = "1. List launches";
    private static final Pattern NUMBERED_BOOKING =
            Pattern.compile("^[1-9][0-9]*\\. .+ - .+ - Seats: .+ - Payment status: .+$");
    private static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter
            .ofPattern("MMMM d, yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final String LAUNCH_DATE = "2099-06-15T10:00:00.000Z";
    private static final String SHOWN_LAUNCH_DATE = "June 15, 2099, 10:00 UTC";
    private static final String LAUNCH_LOCATION = "E2E Pad";
    private static final int LAUNCH_PRICE = 7;
    private static final int LAUNCH_DURATION = 60;
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryFolder;

    @Test
    void AC1_should_show_every_booking_numbered_from_1_with_its_data_when_selecting_either_option() throws Exception {
        Customer customer = registerCustomer();
        CreatedBooking first = createBooking(customer, createLaunch(customer), 1);
        CreatedBooking second = createBooking(customer, createLaunch(customer), 3);
        List<JsonNode> bookings = fetchBookings(customer.token());
        List<String> expectedLines = expectedListLines(bookings);

        ClientRun listed = runClient(apiUrl(), customer.sessionFile(), LIST + EXIT);
        ClientRun viewed = runClient(apiUrl(), customer.sessionFile(), VIEW + CANCEL + EXIT);

        for (ClientRun run : List.of(listed, viewed)) {
            assertEquals(0, run.exitCode(), run::describe);
            assertEquals(expectedLines, run.numberedBookings(), run::describe);
            assertEquals(2, run.timesMenuShown(), run::describe);
            assertTrue(run.lines().stream().anyMatch(line -> line.matches(
                    "[0-9]+\\. " + first.id() + " - " + first.launch().name() + " - Seats: 1 - Payment status: paid")),
                    run::describe);
            assertTrue(run.lines().stream().anyMatch(line -> line.matches(
                    "[0-9]+\\. " + second.id() + " - " + second.launch().name() + " - Seats: 3 - Payment status: paid")),
                    run::describe);
            assertNoStackTrace(run);
        }
        assertEquals(0, listed.count(BOOKING_PROMPT), "The list option must ask for nothing: " + listed.describe());
        assertEquals(1, viewed.count(BOOKING_PROMPT), viewed::describe);
    }

    @Test
    void AC2_should_say_the_user_has_no_bookings_and_ask_for_nothing_when_the_api_returns_none() throws Exception {
        Customer customer = registerCustomer();
        assertTrue(
                fetchBookings(customer.token()).isEmpty(),
                "A new customer holds bookings because ids were reused: restore the API's data as ADR-5 says");

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), LIST + VIEW + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(2, run.count(NO_BOOKINGS_LINE), run::describe);
        assertEquals(0, run.count(BOOKING_PROMPT), run::describe);
        assertTrue(run.numberedBookings().isEmpty(), run::describe);
        assertEquals(3, run.timesMenuShown(), run::describe);
        assertNoStackTrace(run);
    }

    /**
     * A fractional duration and a missing paymentId, which the real API must not be made to hold, are covered by
     * ConsoleMenuTest.should_show_a_fractional_duration_with_no_rounding_when_viewing_a_booking and
     * ConsoleMenuTest.should_say_there_is_no_payment_id_when_the_booking_has_none.
     */
    @Test
    void AC3_should_show_every_field_of_the_booking_when_entering_its_number_in_the_list() throws Exception {
        Customer customer = registerCustomer();
        CreatedBooking other = createBooking(customer, createLaunch(customer), 1);
        CreatedBooking chosen = createBooking(customer, createLaunch(customer), 2);
        int number = numberOf(customer, chosen);
        assertTrue(number > 1, "The chosen booking must not be the first in the list");
        JsonNode expected = fetchBooking(customer.token(), chosen.id());

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), VIEW + number + "\n" + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        String paymentId = expected.get("paymentId").asText();
        assertFalse(paymentId.isBlank(), expected::toString);
        JsonNode launch = expected.get("activity");
        assertEquals(chosen.launch().name(), launch.get("name").asText());
        assertEquals("published", launch.get("status").asText());
        assertInOrder(
                run,
                BOOKING_PROMPT,
                DETAILS_PREFIX + chosen.id(),
                "Seats: 2",
                "Created: " + INSTANT_FORMAT.format(Instant.parse(expected.get("createdAt").asText())),
                "Payment id: " + paymentId,
                "Payment status: " + expected.get("paymentStatus").asText(),
                "Launch: " + chosen.launch().name(),
                "Price: " + LAUNCH_PRICE,
                "Date: " + SHOWN_LAUNCH_DATE,
                "Duration: " + LAUNCH_DURATION,
                "Location: " + LAUNCH_LOCATION,
                "Launch status: published");
        assertEquals(0, run.count(DETAILS_PREFIX + other.id()), run::describe);
        assertEquals(1, run.detailsShown(), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertNoStackTrace(run);
    }

    /**
     * The exact header is checked by
     * ApiClientTest.should_send_a_get_with_the_token_as_a_bearer_header_when_fetching_the_bookings and
     * ApiClientTest.should_request_the_chosen_id_with_the_token_as_a_bearer_header_when_fetching_a_booking.
     */
    @Test
    void AC4_should_show_only_the_bookings_of_the_saved_token_when_the_client_starts_with_a_saved_session()
            throws Exception {
        Customer customer = registerCustomer();
        Customer other = registerCustomer();
        CreatedBooking booking = createBooking(customer, createLaunch(customer), 2);
        int number = numberOf(customer, booking);

        // A new run has only the file to take the token from.
        ClientRun owner = runClient(apiUrl(), customer.sessionFile(), LIST + VIEW + number + "\n" + EXIT);
        ClientRun stranger = runClient(apiUrl(), other.sessionFile(), LIST + VIEW + CANCEL + EXIT);

        assertEquals(0, owner.exitCode(), owner::describe);
        assertEquals(2, owner.numberedLinesWith(booking.launch().name()), owner::describe);
        assertEquals(1, owner.count(DETAILS_PREFIX + booking.id()), owner::describe);
        assertEquals(1, owner.count("Launch: " + booking.launch().name()), owner::describe);
        assertEquals(0, stranger.exitCode(), stranger::describe);
        assertFalse(stranger.stdout().contains(booking.launch().name()), stranger::describe);
        assertEquals(0, stranger.detailsShown(), stranger::describe);
        assertNoStackTrace(owner);
        assertNoStackTrace(stranger);
    }

    /**
     * The absence of a request is checked by
     * ConsoleMenuTest.should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_listing_the_bookings_with_no_session
     * and ConsoleMenuTest.should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_viewing_a_booking_with_no_session.
     */
    @Test
    void AC5_should_tell_the_user_to_register_or_log_in_when_selecting_either_option_with_no_session() {
        Path noSessionFile = temporaryFolder.resolve("no-session.json");

        ClientRun run = runClient(apiUrl(), noSessionFile, LIST + VIEW + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(2, run.count(NO_SESSION_LINE), run::describe);
        assertEquals(0, run.count(BOOKING_PROMPT), run::describe);
        assertTrue(run.numberedBookings().isEmpty(), run::describe);
        assertEquals(3, run.timesMenuShown(), run::describe);
        assertFalse(Files.exists(noSessionFile), run::describe);
        assertNoStackTrace(run);
    }

    /** The absence of a request is checked by ConsoleMenuTest.should_ask_again_without_a_request_when_the_booking_number_is_invalid. */
    @Test
    void AC6_should_ask_again_when_the_entry_is_not_0_and_not_a_number_in_the_list() throws Exception {
        Customer customer = registerCustomer();
        CreatedBooking booking = createBooking(customer, createLaunch(customer), 1);
        int size = fetchBookings(customer.token()).size();

        ClientRun run = runClient(
                apiUrl(), customer.sessionFile(), VIEW + (size + 1) + "\ntext\n-1\n1.5\n\n" + CANCEL + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(6, run.count(BOOKING_PROMPT), run::describe);
        assertEquals(5, run.count(INVALID_NUMBER_LINE), run::describe);
        assertEquals(size, run.numberedBookings().size(), "The list must be shown only once: " + run.describe());
        assertEquals(0, run.detailsShown(), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertNoStackTrace(run);

        // A valid number still works after an invalid one, with no second list.
        ClientRun corrected = runClient(
                apiUrl(), customer.sessionFile(), VIEW + "text\n" + numberOf(customer, booking) + "\n" + EXIT);

        assertEquals(2, corrected.count(BOOKING_PROMPT), corrected::describe);
        assertEquals(1, corrected.count(INVALID_NUMBER_LINE), corrected::describe);
        assertEquals(size, corrected.numberedBookings().size(), corrected::describe);
        assertEquals(1, corrected.count(DETAILS_PREFIX + booking.id()), corrected::describe);
    }

    /**
     * A real token cannot be rejected between the list and the chosen booking, so the 401 on GET /bookings/:id is
     * covered by ConsoleMenuTest.should_delete_the_file_and_ask_to_register_again_when_viewing_the_booking_responds_401.
     */
    @Test
    void AC7_should_delete_the_file_and_ask_to_register_again_when_the_api_rejects_the_saved_token() throws Exception {
        for (String option : List.of(LIST, VIEW)) {
            Path sessionFile = writeSessionFile("rejected-" + option.trim() + ".json", "not-a-valid-token");

            // Selecting the option again proves that the session is gone, in this run too.
            ClientRun run = runClient(apiUrl(), sessionFile, option + option + EXIT);

            assertEquals(0, run.exitCode(), run::describe);
            assertInOrder(run, SESSION_REJECTED_LINE, NO_SESSION_LINE);
            assertEquals(1, run.count(SESSION_REJECTED_LINE), run::describe);
            assertEquals(0, run.count(BOOKING_PROMPT), run::describe);
            assertFalse(Files.exists(sessionFile), run::describe);
            assertEquals(3, run.timesMenuShown(), run::describe);
            assertNoStackTrace(run);
        }
    }

    /**
     * Deleting the launch once the list is on screen makes GET /bookings/:id respond 500, and from then on
     * GET /bookings too. The 404 of a booking that no longer exists cannot be reached, because the API cannot delete
     * bookings: ConsoleMenuTest.should_show_the_message_and_the_menu_again_when_the_chosen_booking_no_longer_exists
     * covers it.
     */
    @Test
    void AC8_should_show_the_api_error_and_the_menu_again_when_either_request_responds_with_an_error() throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch launch = createLaunch(customer);
        CreatedBooking booking = createBooking(customer, launch, 1);
        int number = numberOf(customer, booking);
        AtomicInteger deleteStatus = new AtomicInteger();

        ClientRun chosen = runClient(
                apiUrl(),
                customer.sessionFile(),
                new OneLinePerRead(
                        List.of(VIEW, number + "\n", EXIT),
                        1,
                        () -> deleteStatus.set(deleteLaunch(customer, launch))));

        assertEquals(204, deleteStatus.get(), chosen::describe);
        assertEquals(0, chosen.exitCode(), chosen::describe);
        assertEquals(1, chosen.numberedLinesWith(launch.name()), chosen::describe);
        assertEquals(1, chosen.count(VIEW_FAILED_LINE), chosen::describe);
        assertEquals(0, chosen.detailsShown(), chosen::describe);
        assertEquals(2, chosen.timesMenuShown(), chosen::describe);
        assertTrue(Files.exists(customer.sessionFile()), chosen::describe);
        assertNoStackTrace(chosen);

        ClientRun listed = runClient(apiUrl(), customer.sessionFile(), LIST + VIEW + EXIT);

        assertEquals(0, listed.exitCode(), listed::describe);
        assertEquals(2, listed.count(LIST_FAILED_LINE), listed::describe);
        assertEquals(0, listed.count(BOOKING_PROMPT), listed::describe);
        assertTrue(listed.numberedBookings().isEmpty(), listed::describe);
        assertEquals(3, listed.timesMenuShown(), listed::describe);
        assertTrue(Files.exists(customer.sessionFile()), listed::describe);
        assertNoStackTrace(listed);
    }

    /**
     * The API cannot go down between the list and the chosen booking in an e2e run, so GET /bookings/:id is covered by
     * ConsoleMenuTest.should_show_not_reachable_and_the_menu_again_when_the_api_goes_down_before_the_booking_is_requested.
     */
    @Test
    void AC9_should_show_not_reachable_and_the_menu_again_when_nothing_listens() throws Exception {
        String url = urlWithNothingListening();
        Path sessionFile = writeSessionFile("unreachable.json", "any-token");
        String savedSession = Files.readString(sessionFile);

        ClientRun run = runClient(url, sessionFile, LIST + VIEW + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(2, run.count("API not reachable at " + url), run::describe);
        assertEquals(0, run.count(BOOKING_PROMPT), run::describe);
        assertEquals(3, run.timesMenuShown(), run::describe);
        assertEquals(savedSession, Files.readString(sessionFile), run::describe);
        assertNoStackTrace(run);
    }

    /** The absence of a request is checked by ConsoleMenuTest.should_cancel_without_requesting_the_booking_when_the_booking_number_is_0. */
    @Test
    void AC10_should_cancel_and_return_to_the_menu_when_the_user_enters_0_at_the_prompt() throws Exception {
        Customer customer = registerCustomer();
        createBooking(customer, createLaunch(customer), 1);

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), VIEW + CANCEL + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(1, run.count(BOOKING_PROMPT), run::describe);
        assertEquals(0, run.count(INVALID_NUMBER_LINE), run::describe);
        assertEquals(0, run.detailsShown(), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertNoStackTrace(run);
    }

    /**
     * Pins, against the real API, the behaviour the plan chose for a booking with fractional seats, which the API
     * accepts from another client: the booking is malformed for the client, so the whole list becomes the generic error.
     */
    @Test
    void PlanPin_should_show_the_generic_error_and_the_menu_again_when_a_booking_has_fractional_seats() throws Exception {
        Customer customer = registerCustomer();
        createBooking(customer, createLaunch(customer), 1.5);

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), LIST + VIEW + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(2, run.count(GENERIC_ERROR_LINE), run::describe);
        assertEquals(0, run.count(BOOKING_PROMPT), run::describe);
        assertEquals(3, run.timesMenuShown(), run::describe);
        assertNoStackTrace(run);
    }

    /** Extends AC5 of feat-launch-listing, on the word "activity", to the texts this spec adds. */
    @Test
    void LaunchListingAC5_should_not_say_activity_when_retrieving_bookings() throws Exception {
        Customer customer = registerCustomer();
        Customer empty = registerCustomer();
        CreatedLaunch launch = createLaunch(customer);
        CreatedBooking booking = createBooking(customer, launch, 2);
        int number = numberOf(customer, booking);
        Path rejected = writeSessionFile("rejected-texts.json", "not-a-valid-token");
        Path unreachable = writeSessionFile("unreachable-texts.json", "any-token");

        List<ClientRun> runs = List.of(
                runClient(apiUrl(), customer.sessionFile(), LIST + VIEW + "text\n" + number + "\n" + VIEW + CANCEL + EXIT),
                runClient(apiUrl(), empty.sessionFile(), LIST + VIEW + EXIT),
                runClient(apiUrl(), temporaryFolder.resolve("no-session-texts.json"), LIST + VIEW + EXIT),
                runClient(apiUrl(), rejected, LIST + EXIT),
                runClient(urlWithNothingListening(), unreachable, LIST + VIEW + EXIT));

        assertEquals(1, runs.get(0).count(DETAILS_PREFIX + booking.id()), runs.get(0)::describe);
        for (ClientRun run : runs) {
            assertFalse(run.stdout().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
            assertFalse(run.stderr().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
        }
    }

    private Customer registerCustomer() throws IOException {
        String email = "e2e-" + System.currentTimeMillis() + "-" + COUNTER.incrementAndGet() + "@example.com";
        Path sessionFile = temporaryFolder.resolve(email + ".json");
        String script = "2\n" + USERNAME + "\n" + email + "\n" + PASSWORD + "\ny\n" + EXIT;

        ClientRun run = runClient(apiUrl(), sessionFile, script);

        assertTrue(Files.exists(sessionFile), run::describe);
        return new Customer(sessionFile, JSON.readTree(Files.readString(sessionFile)).get("accessToken").asText());
    }

    private Path writeSessionFile(String fileName, String token) throws IOException {
        Path sessionFile = temporaryFolder.resolve(fileName);
        Files.writeString(sessionFile, JSON.writeValueAsString(Map.of(
                "accessToken", token, "username", USERNAME, "email", "e2e-saved@example.com")));
        return sessionFile;
    }

    private static CreatedLaunch createLaunch(Customer customer) throws IOException, InterruptedException {
        String name = "E2E Launch " + System.currentTimeMillis() + "-" + COUNTER.incrementAndGet();
        String body = JSON.writeValueAsString(Map.of(
                "name", name,
                "price", LAUNCH_PRICE,
                "date", LAUNCH_DATE,
                "duration", LAUNCH_DURATION,
                "location", LAUNCH_LOCATION,
                "minParticipants", 1,
                "maxParticipants", 50,
                "status", "published"));
        HttpResponse<String> response = post("/activities", customer.token(), body);
        assertEquals(201, response.statusCode(), response::body);
        return new CreatedLaunch(JSON.readTree(response.body()).get("id").asText(), name);
    }

    private static CreatedBooking createBooking(Customer customer, CreatedLaunch launch, Number participants)
            throws IOException, InterruptedException {
        String body = JSON.writeValueAsString(Map.of("activityId", launch.id(), "participants", participants));
        HttpResponse<String> response = post("/bookings", customer.token(), body);
        assertEquals(201, response.statusCode(), response::body);
        return new CreatedBooking(JSON.readTree(response.body()).get("id").asText(), launch);
    }

    private static int deleteLaunch(Customer customer, CreatedLaunch launch) {
        HttpRequest request = authorized("/activities/" + launch.id(), customer.token()).DELETE().build();
        try {
            return send(request).statusCode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static List<JsonNode> fetchBookings(String token) throws IOException, InterruptedException {
        JsonNode body = get("/bookings", token);
        assertTrue(body.isArray(), body::toString);
        List<JsonNode> items = new ArrayList<>();
        body.forEach(items::add);
        return items;
    }

    private static JsonNode fetchBooking(String token, String id) throws IOException, InterruptedException {
        return get("/bookings/" + id, token);
    }

    /** The booking's number in the list the menu shows, which keeps the API's order. */
    private static int numberOf(Customer customer, CreatedBooking booking) throws IOException, InterruptedException {
        List<JsonNode> bookings = fetchBookings(customer.token());
        for (int index = 0; index < bookings.size(); index++) {
            if (bookings.get(index).get("id").asText().equals(booking.id())) {
                return index + 1;
            }
        }
        throw new AssertionError("Booking " + booking.id() + " is not in GET /bookings: " + bookings);
    }

    private static List<String> expectedListLines(List<JsonNode> bookings) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < bookings.size(); index++) {
            JsonNode booking = bookings.get(index);
            lines.add((index + 1) + ". " + booking.get("id").asText()
                    + " - " + booking.get("activity").get("name").asText()
                    + " - Seats: " + booking.get("participants").asInt()
                    + " - Payment status: " + booking.get("paymentStatus").asText());
        }
        return lines;
    }

    private static JsonNode get(String path, String token) throws IOException, InterruptedException {
        HttpResponse<String> response = send(authorized(path, token).GET().build());
        assertEquals(200, response.statusCode(), response::body);
        return JSON.readTree(response.body());
    }

    private static HttpResponse<String> post(String path, String token, String body)
            throws IOException, InterruptedException {
        return send(authorized(path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private static HttpRequest.Builder authorized(String path, String token) {
        return HttpRequest.newBuilder(URI.create(apiUrl() + path)).header("Authorization", "Bearer " + token);
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertInOrder(ClientRun run, String... expectedLines) {
        int previous = -1;
        for (String expected : expectedLines) {
            int found = run.lines().indexOf(expected);
            assertTrue(
                    found > previous,
                    () -> "Expected in order " + List.of(expectedLines) + ", at " + expected + ", in " + run.describe());
            previous = found;
        }
    }

    private static void assertNoStackTrace(ClientRun run) {
        for (String text : List.of(run.stdout(), run.stderr())) {
            assertFalse(text.contains("Exception"), run::describe);
            assertFalse(text.contains("Caused by"), run::describe);
            assertFalse(text.contains("\tat "), run::describe);
        }
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

    private static ClientRun runClient(String url, Path sessionFile, String script) {
        return runClient(url, sessionFile, new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)));
    }

    private static ClientRun runClient(String url, Path sessionFile, InputStream input) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            int exitCode = Main.run(
                    Map.of(VARIABLE, url),
                    input,
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    sessionFile);
            return new ClientRun(
                    exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalErr);
        }
    }

    /**
     * Delivers each line in its own read, because a buffered reader reads ahead, and runs an action just before
     * handing over one of them, by then the client has shown everything up to that prompt.
     */
    private static final class OneLinePerRead extends InputStream {

        private final List<String> lines;
        private final int lineBeforeAction;
        private final Runnable action;
        private int next;

        OneLinePerRead(List<String> lines, int lineBeforeAction, Runnable action) {
            this.lines = lines;
            this.lineBeforeAction = lineBeforeAction;
            this.action = action;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException("The client reads through a buffer");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (next == lines.size()) {
                return -1;
            }
            if (next == lineBeforeAction) {
                action.run();
            }
            byte[] line = lines.get(next++).getBytes(StandardCharsets.UTF_8);
            System.arraycopy(line, 0, buffer, offset, line.length);
            return line.length;
        }
    }

    private record Customer(Path sessionFile, String token) {
    }

    private record CreatedLaunch(String id, String name) {
    }

    private record CreatedBooking(String id, CreatedLaunch launch) {
    }

    private record ClientRun(int exitCode, String stdout, String stderr) {

        List<String> lines() {
            return stdout.lines().toList();
        }

        long count(String line) {
            return lines().stream().filter(line::equals).count();
        }

        long timesMenuShown() {
            return count(MENU_LINE);
        }

        long detailsShown() {
            return lines().stream().filter(line -> line.startsWith(DETAILS_PREFIX)).count();
        }

        List<String> numberedBookings() {
            return lines().stream().filter(line -> NUMBERED_BOOKING.matcher(line).matches()).toList();
        }

        long numberedLinesWith(String launchName) {
            return numberedBookings().stream().filter(line -> line.contains(" - " + launchName + " - ")).count();
        }

        String describe() {
            return "exit code " + exitCode + ", stdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }
}
