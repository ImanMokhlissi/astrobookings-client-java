package dev.iman.astrobookings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance criteria of feat-booking-creation, against the real API. AC2 and AC12 have no test here,
 * because the real API cannot reach them: the menu only offers launches that pass the filter, and the
 * seeded launches stay published. BookingServiceTest and ConsoleMenuTest cover them.
 *
 * <p>Each test that books creates its own launch, because the seeded ones run out of seats and cannot
 * reach a 402. A launch that must not be rejected costs 7, so that its total is not a multiple of 1000.
 */
@Tag("e2e")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class BookingCreationE2eTest {

    private static final String VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final String USERNAME = "e2e-user";
    private static final String PASSWORD = "E2e-Secret-Pass-1";
    private static final String BOOK = "5\n";
    private static final String EXIT = "0\n";
    private static final String CANCEL = "0\n";
    private static final String LAUNCH_PROMPT = "Launch number (0 to cancel):";
    private static final String SEATS_PROMPT = "Seats (0 to cancel):";
    private static final String INVALID_LAUNCH_LINE = "Enter a launch number from the list.";
    private static final String INVALID_SEATS_LINE = "The seats must be a whole number of at least 1.";
    private static final String CREATED_LINE = "Booking created.";
    private static final String NOT_CREATED_LINE = "No booking was created.";
    private static final String PAYMENT_REJECTED_LINE = "Payment could not be processed";
    private static final String NO_SESSION_LINE = "Register or log in first.";
    private static final String SESSION_REJECTED_LINE = "Your session is no longer valid. Please register again.";
    private static final String MENU_LINE = "1. List launches";
    private static final Set<String> OPEN_STATUSES = Set.of("published", "confirmed");
    private static final Pattern NUMBERED_LAUNCH = Pattern.compile("^[1-9][0-9]*\\. .+ - Price: .+$");
    private static final int CHEAP_PRICE = 7;
    private static final int ROOMY = 50;
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryFolder;

    @Test
    void AC1_should_offer_only_the_published_and_confirmed_launches_as_a_numbered_list_when_selecting_the_option()
            throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch published = createLaunch(customer, "published", CHEAP_PRICE, ROOMY);
        CreatedLaunch confirmed = createLaunch(customer, "confirmed", CHEAP_PRICE, ROOMY);
        for (String closedStatus : List.of("draft", "sold-out", "done", "cancelled")) {
            createLaunch(customer, closedStatus, CHEAP_PRICE, ROOMY);
        }
        List<JsonNode> activities = fetchActivities();

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), BOOK + CANCEL + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        List<String> expectedNames = activities.stream()
                .filter(activity -> OPEN_STATUSES.contains(activity.get("status").asText()))
                .map(activity -> activity.get("name").asText())
                .toList();
        List<String> numbered = run.numberedLaunches();
        assertEquals(expectedNames.size(), numbered.size(), run::describe);
        for (int index = 0; index < expectedNames.size(); index++) {
            String expectedStart = (index + 1) + ". " + expectedNames.get(index) + " - ";
            String shown = numbered.get(index);
            assertTrue(shown.startsWith(expectedStart), () -> "Expected " + expectedStart + " in " + shown);
        }
        assertTrue(run.showsLaunch(numberOf(published), published.name()), run::describe);
        assertTrue(run.showsLaunch(numberOf(confirmed), confirmed.name()), run::describe);
        List<String> closedNames = activities.stream()
                .filter(activity -> !OPEN_STATUSES.contains(activity.get("status").asText()))
                .map(activity -> activity.get("name").asText())
                .toList();
        assertFalse(closedNames.isEmpty(), "The API must hold launches that are not open for this check");
        for (String closedName : closedNames) {
            assertFalse(numbered.stream().anyMatch(line -> line.contains(". " + closedName + " - ")), run::describe);
        }
        assertNoStackTrace(run);
    }

    @Test
    void AC3_should_send_the_chosen_launch_id_and_the_seats_when_choosing_a_launch_that_is_not_the_first()
            throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch launch = createLaunch(customer, "published", CHEAP_PRICE, ROOMY);
        int number = numberOf(launch);
        assertTrue(number > 1, "The created launch must not be the first in the list");
        List<String> before = bookingIds(customer.token());

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), BOOK + number + "\n2\n" + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertTrue(run.showsLaunch(number, launch.name()), run::describe);
        JsonNode booking = onlyNewBooking(customer.token(), before);
        assertEquals(launch.id(), booking.get("activityId").asText());
        assertEquals(2, booking.get("participants").asInt());
        assertNoStackTrace(run);
    }

    /** The exact header is checked by ApiClientTest.should_send_the_token_as_a_bearer_header_when_creating_a_booking. */
    @Test
    void AC4_should_book_as_the_owner_of_the_saved_token_when_the_client_starts_with_a_saved_session() throws Exception {
        Customer customer = registerCustomer();
        Customer other = registerCustomer();
        CreatedLaunch launch = createLaunch(customer, "published", CHEAP_PRICE, ROOMY);
        List<String> before = bookingIds(customer.token());

        // A new run has only the file to take the token from.
        ClientRun run = runClient(apiUrl(), customer.sessionFile(), BOOK + numberOf(launch) + "\n1\n" + EXIT);

        assertTrue(run.lines().contains(CREATED_LINE), run::describe);
        assertEquals(launch.id(), onlyNewBooking(customer.token(), before).get("activityId").asText());
        assertTrue(bookingsOnLaunch(other.token(), launch).isEmpty(), "The booking must belong to the saved token");
    }

    @Test
    void AC5_should_show_the_id_launch_name_seats_and_payment_status_when_the_api_responds_201() throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch launch = createLaunch(customer, "published", CHEAP_PRICE, ROOMY);
        List<String> before = bookingIds(customer.token());

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), BOOK + numberOf(launch) + "\n2\n" + EXIT);

        JsonNode booking = onlyNewBooking(customer.token(), before);
        assertInOrder(
                run,
                CREATED_LINE,
                "Booking id: " + booking.get("id").asText(),
                "Launch: " + launch.name(),
                "Seats: 2",
                "Payment status: paid");
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertNoStackTrace(run);
    }

    /** Also covered by ConsoleMenuTest.should_ask_again_without_a_request_when_the_launch_number_is_invalid. */
    @Test
    void AC6_should_ask_again_and_send_nothing_when_the_launch_entry_is_not_in_the_list() throws Exception {
        Customer customer = registerCustomer();
        int pastTheList = openLaunches().size() + 1;
        List<String> before = bookingIds(customer.token());

        ClientRun run = runClient(
                apiUrl(), customer.sessionFile(), BOOK + pastTheList + "\ntext\n-1\n1.5\n\n" + CANCEL + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(6, run.count(LAUNCH_PROMPT), run::describe);
        assertEquals(5, run.count(INVALID_LAUNCH_LINE), run::describe);
        assertEquals(0, run.count(SEATS_PROMPT), run::describe);
        assertEquals(openLaunches().size(), run.numberedLaunches().size(), "The list must be shown only once");
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertEquals(before, bookingIds(customer.token()));
        assertNoStackTrace(run);

        // A valid number still works after an invalid one.
        ClientRun corrected = runClient(apiUrl(), customer.sessionFile(), BOOK + "text\n1\n" + CANCEL + EXIT);

        assertEquals(2, corrected.count(LAUNCH_PROMPT), corrected::describe);
        assertEquals(1, corrected.count(SEATS_PROMPT), corrected::describe);
        assertEquals(before, bookingIds(customer.token()));
    }

    @Test
    void AC7_should_show_the_capacity_message_unchanged_when_the_seats_exceed_what_is_available() throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch launch = createLaunch(customer, "published", CHEAP_PRICE, 2);
        List<String> before = bookingIds(customer.token());

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), BOOK + numberOf(launch) + "\n3\n" + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(1, run.count("Capacity exceeded. Available: 2, Requested: 3"), run::describe);
        assertEquals(0, run.count(CREATED_LINE), run::describe);
        assertEquals(0, run.count(NOT_CREATED_LINE), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertEquals(before, bookingIds(customer.token()));
        assertNoStackTrace(run);
    }

    @Test
    void AC8_should_show_the_message_and_say_no_booking_was_created_when_the_api_responds_402() throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch launch = createLaunch(customer, "published", 500, ROOMY);
        List<String> before = bookingIds(customer.token());

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), BOOK + numberOf(launch) + "\n2\n" + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertInOrder(run, PAYMENT_REJECTED_LINE, NOT_CREATED_LINE);
        assertEquals(0, run.count(CREATED_LINE), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertEquals(before, bookingIds(customer.token()));
        assertNoStackTrace(run);
    }

    /**
     * The absence of a request is checked by
     * ConsoleMenuTest.should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_booking_with_no_session.
     */
    @Test
    void AC9_should_tell_the_user_to_register_or_log_in_when_there_is_no_saved_session() {
        Path noSessionFile = temporaryFolder.resolve("no-session.json");

        ClientRun run = runClient(apiUrl(), noSessionFile, BOOK + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(1, run.count(NO_SESSION_LINE), run::describe);
        assertEquals(0, run.count(LAUNCH_PROMPT), run::describe);
        assertTrue(run.numberedLaunches().isEmpty(), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertFalse(Files.exists(noSessionFile), run::describe);
        assertNoStackTrace(run);
    }

    @Test
    void AC10_should_ask_again_and_send_nothing_when_the_seats_are_not_a_whole_number_of_at_least_1() throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch launch = createLaunch(customer, "published", CHEAP_PRICE, ROOMY);
        int number = numberOf(launch);
        List<String> before = bookingIds(customer.token());

        ClientRun run = runClient(
                apiUrl(), customer.sessionFile(), BOOK + number + "\n-1\nabc\n1.5\n\n+2\n" + CANCEL + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(6, run.count(SEATS_PROMPT), run::describe);
        assertEquals(5, run.count(INVALID_SEATS_LINE), run::describe);
        assertEquals(0, run.count(CREATED_LINE), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertEquals(before, bookingIds(customer.token()));
        assertNoStackTrace(run);

        // A valid number of seats still books after an invalid one.
        ClientRun corrected = runClient(apiUrl(), customer.sessionFile(), BOOK + number + "\nabc\n2\n" + EXIT);

        assertEquals(2, corrected.count(SEATS_PROMPT), corrected::describe);
        assertEquals(2, onlyNewBooking(customer.token(), before).get("participants").asInt());
    }

    @Test
    void AC11_should_cancel_and_return_to_the_menu_when_the_user_enters_0_at_either_prompt() throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch launch = createLaunch(customer, "published", CHEAP_PRICE, ROOMY);
        List<String> before = bookingIds(customer.token());

        ClientRun atLaunch = runClient(apiUrl(), customer.sessionFile(), BOOK + CANCEL + EXIT);

        assertEquals(0, atLaunch.exitCode(), atLaunch::describe);
        assertEquals(1, atLaunch.count(LAUNCH_PROMPT), atLaunch::describe);
        assertEquals(0, atLaunch.count(SEATS_PROMPT), atLaunch::describe);
        assertEquals(2, atLaunch.timesMenuShown(), atLaunch::describe);
        assertEquals(before, bookingIds(customer.token()));
        assertNoStackTrace(atLaunch);

        ClientRun atSeats = runClient(apiUrl(), customer.sessionFile(), BOOK + numberOf(launch) + "\n" + CANCEL + EXIT);

        assertEquals(0, atSeats.exitCode(), atSeats::describe);
        assertEquals(1, atSeats.count(SEATS_PROMPT), atSeats::describe);
        assertEquals(0, atSeats.count(CREATED_LINE), atSeats::describe);
        assertEquals(2, atSeats.timesMenuShown(), atSeats::describe);
        assertEquals(before, bookingIds(customer.token()));
        assertNoStackTrace(atSeats);
    }

    /** Criterion AC9 of feat-session, which this is the first authenticated call to reach. */
    @Test
    void SessionAC9_should_delete_the_file_and_ask_to_register_again_when_the_api_rejects_the_saved_token()
            throws Exception {
        Customer owner = registerCustomer();
        CreatedLaunch launch = createLaunch(owner, "published", CHEAP_PRICE, ROOMY);
        Path sessionFile = writeSessionFile("invalid-token.json", "not-a-valid-token");

        ClientRun run = runClient(apiUrl(), sessionFile, BOOK + numberOf(launch) + "\n2\n" + BOOK + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertInOrder(run, SESSION_REJECTED_LINE, NO_SESSION_LINE);
        assertEquals(0, run.count(CREATED_LINE), run::describe);
        assertFalse(Files.exists(sessionFile), run::describe);
        assertEquals(3, run.timesMenuShown(), run::describe);
        assertNoStackTrace(run);
    }

    @Test
    void Unreachable_should_show_not_reachable_and_the_menu_again_when_nothing_listens() throws Exception {
        String url = urlWithNothingListening();
        Path sessionFile = writeSessionFile("unreachable.json", "any-token");

        ClientRun run = runClient(url, sessionFile, BOOK + EXIT);

        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(1, run.count("API not reachable at " + url), run::describe);
        assertEquals(0, run.count(LAUNCH_PROMPT), run::describe);
        assertEquals(2, run.timesMenuShown(), run::describe);
        assertTrue(Files.exists(sessionFile), run::describe);
        assertNoStackTrace(run);
    }

    /** Extends AC5 of feat-launch-listing, on the word "activity", to the texts this spec adds. */
    @Test
    void LaunchListingAC5_should_not_say_activity_when_booking() throws Exception {
        Customer customer = registerCustomer();
        CreatedLaunch cheap = createLaunch(customer, "published", CHEAP_PRICE, ROOMY);
        CreatedLaunch small = createLaunch(customer, "published", CHEAP_PRICE, 2);
        CreatedLaunch rejected = createLaunch(customer, "published", 500, ROOMY);
        String script = BOOK + "text\n" + numberOf(cheap) + "\nabc\n2\n"
                + BOOK + numberOf(small) + "\n3\n"
                + BOOK + numberOf(rejected) + "\n2\n"
                + BOOK + CANCEL
                + EXIT;

        ClientRun run = runClient(apiUrl(), customer.sessionFile(), script);

        assertEquals(1, run.count(CREATED_LINE), run::describe);
        assertEquals(1, run.count(NOT_CREATED_LINE), run::describe);
        assertEquals(1, run.count("Capacity exceeded. Available: 2, Requested: 3"), run::describe);
        assertFalse(run.stdout().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
        assertFalse(run.stderr().toLowerCase(Locale.ROOT).contains("activit"), run::describe);
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

    private static CreatedLaunch createLaunch(Customer customer, String status, int price, int maxParticipants)
            throws IOException, InterruptedException {
        String name = "E2E Launch " + System.currentTimeMillis() + "-" + COUNTER.incrementAndGet();
        String body = JSON.writeValueAsString(Map.of(
                "name", name,
                "price", price,
                "date", "2099-06-15T10:00:00.000Z",
                "duration", 60,
                "location", "E2E Pad",
                "minParticipants", 1,
                "maxParticipants", maxParticipants,
                "status", status));
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl() + "/activities"))
                .header("Authorization", "Bearer " + customer.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send(request);
        assertEquals(201, response.statusCode(), response::body);
        return new CreatedLaunch(JSON.readTree(response.body()).get("id").asText(), name);
    }

    private static List<JsonNode> fetchActivities() throws IOException, InterruptedException {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(apiUrl() + "/activities")).GET().build());
        assertEquals(200, response.statusCode(), response::body);
        return toList(JSON.readTree(response.body()));
    }

    private static List<JsonNode> openLaunches() throws IOException, InterruptedException {
        return fetchActivities().stream()
                .filter(activity -> OPEN_STATUSES.contains(activity.get("status").asText()))
                .toList();
    }

    /** The launch's number in the list the menu shows, which keeps the API's order. */
    private static int numberOf(CreatedLaunch launch) throws IOException, InterruptedException {
        List<JsonNode> open = openLaunches();
        for (int index = 0; index < open.size(); index++) {
            if (open.get(index).get("id").asText().equals(launch.id())) {
                return index + 1;
            }
        }
        throw new AssertionError("Launch " + launch.id() + " is not open for booking");
    }

    private static List<JsonNode> fetchBookings(String token) throws IOException, InterruptedException {
        return fetchBookings(token, "/bookings");
    }

    private static List<JsonNode> fetchBookings(String token, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl() + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        assertEquals(200, response.statusCode(), response::body);
        return toList(JSON.readTree(response.body()));
    }

    /** Ids reused after an API restart can show a new user old bookings, so tests compare before and after. */
    private static List<String> bookingIds(String token) throws IOException, InterruptedException {
        return fetchBookings(token).stream().map(booking -> booking.get("id").asText()).toList();
    }

    private static List<JsonNode> bookingsOnLaunch(String token, CreatedLaunch launch)
            throws IOException, InterruptedException {
        return fetchBookings(token, "/bookings?activityId=" + launch.id());
    }

    private static JsonNode onlyNewBooking(String token, List<String> before) throws IOException, InterruptedException {
        List<JsonNode> created = fetchBookings(token).stream()
                .filter(booking -> !before.contains(booking.get("id").asText()))
                .toList();
        assertEquals(1, created.size(), () -> "Expected exactly one new booking, found " + created);
        return created.get(0);
    }

    private static List<JsonNode> toList(JsonNode array) {
        assertTrue(array.isArray(), array::toString);
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertInOrder(ClientRun run, String... expectedLines) {
        int previous = -1;
        for (String expected : expectedLines) {
            int found = run.lines().indexOf(expected);
            assertTrue(found > previous, () -> "Expected in order " + List.of(expectedLines) + " in " + run.describe());
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
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            int exitCode = Main.run(
                    Map.of(VARIABLE, url),
                    new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    sessionFile);
            return new ClientRun(
                    exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalErr);
        }
    }

    private record Customer(Path sessionFile, String token) {
    }

    private record CreatedLaunch(String id, String name) {
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

        List<String> numberedLaunches() {
            return lines().stream().filter(line -> NUMBERED_LAUNCH.matcher(line).matches()).toList();
        }

        boolean showsLaunch(int number, String name) {
            return lines().stream().anyMatch(line -> line.startsWith(number + ". " + name + " - "));
        }

        String describe() {
            return "exit code " + exitCode + ", stdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }
}
