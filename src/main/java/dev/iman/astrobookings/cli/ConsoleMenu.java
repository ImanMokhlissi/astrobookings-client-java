package dev.iman.astrobookings.cli;

import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.ApiNotReachableFailure;
import dev.iman.astrobookings.model.InvalidCredentialsFailure;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.LaunchNotBookableFailure;
import dev.iman.astrobookings.model.NoSessionFailure;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.SessionFileFailure;
import dev.iman.astrobookings.model.SessionRejectedFailure;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchService;
import dev.iman.astrobookings.service.SessionService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** The interactive menu. It runs until the user exits or the input ends. */
public final class ConsoleMenu {

    static final String NO_OPEN_LAUNCHES_MESSAGE = "No launches open for booking.";

    private static final String LIST_LAUNCHES_OPTION = "1";
    private static final String REGISTER_OPTION = "2";
    private static final String LOG_IN_OPTION = "3";
    private static final String LOG_OUT_OPTION = "4";
    private static final String BOOK_SEATS_OPTION = "5";
    private static final String LIST_BOOKINGS_OPTION = "6";
    private static final String VIEW_BOOKING_OPTION = "7";
    private static final String EXIT_OPTION = "0";
    private static final String EMAIL_FIELD = "email";
    private static final String EMAIL_PROMPT = "Email:";
    private static final String PASSWORD_PROMPT = "Password:";
    private static final Set<String> YES_ANSWERS = Set.of("y", "yes");

    private final LaunchService launchService;
    private final SessionService sessionService;
    private final BufferedReader input;
    private final PrintStream output;
    private final PasswordPrompt passwordPrompt;
    private final BookingFlow bookingFlow;
    private final BookingRetrievalFlow bookingRetrievalFlow;

    /** Reads passwords from the injected input, for when there is no console. */
    public ConsoleMenu(
            LaunchService launchService,
            SessionService sessionService,
            BookingService bookingService,
            InputStream input,
            PrintStream output) {
        this.launchService = launchService;
        this.sessionService = sessionService;
        this.input = readerOver(input);
        this.output = output;
        this.passwordPrompt = this::readPasswordFromInput;
        this.bookingFlow = new BookingFlow(bookingService, newNumberPrompt(), output, this::showFailure);
        this.bookingRetrievalFlow =
                new BookingRetrievalFlow(bookingService, newNumberPrompt(), output, this::showFailure);
    }

    public ConsoleMenu(
            LaunchService launchService,
            SessionService sessionService,
            BookingService bookingService,
            InputStream input,
            PrintStream output,
            PasswordPrompt passwordPrompt) {
        this.launchService = launchService;
        this.sessionService = sessionService;
        this.input = readerOver(input);
        this.output = output;
        this.passwordPrompt = passwordPrompt;
        this.bookingFlow = new BookingFlow(bookingService, newNumberPrompt(), output, this::showFailure);
        this.bookingRetrievalFlow =
                new BookingRetrievalFlow(bookingService, newNumberPrompt(), output, this::showFailure);
    }

    private static BufferedReader readerOver(InputStream input) {
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private NumberPrompt newNumberPrompt() {
        return new NumberPrompt(this::promptFor, output);
    }

    public void run() {
        loadSavedSession();
        while (true) {
            showMenu();
            Optional<String> option = readTrimmedLine();
            if (option.isEmpty() || option.get().equals(EXIT_OPTION)) {
                return;
            }
            handleOption(option.get());
        }
    }

    private void loadSavedSession() {
        try {
            sessionService.loadSavedSession();
        } catch (SessionFileFailure failure) {
            output.println("The session file could not be read. Continuing with no session.");
        }
    }

    private void showMenu() {
        output.println();
        output.println("AstroBookings");
        sessionService.currentSession().ifPresent(this::showLoggedIn);
        output.println(LIST_LAUNCHES_OPTION + ". List launches");
        output.println(REGISTER_OPTION + ". Register");
        output.println(LOG_IN_OPTION + ". Log in");
        output.println(LOG_OUT_OPTION + ". Log out");
        output.println(BOOK_SEATS_OPTION + ". Book seats");
        output.println(LIST_BOOKINGS_OPTION + ". List my bookings");
        output.println(VIEW_BOOKING_OPTION + ". View a booking");
        output.println(EXIT_OPTION + ". Exit");
        output.println("Select an option:");
    }

    private void showLoggedIn(Session session) {
        output.println("Logged in as " + session.username() + " (" + session.email() + ")");
    }

    /** An empty result means that the input ended, which a read error also counts as. */
    private Optional<String> readTrimmedLine() {
        return readRawLine().map(String::trim);
    }

    private Optional<String> readRawLine() {
        try {
            return Optional.ofNullable(input.readLine());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<String> promptFor(String prompt) {
        output.println(prompt);
        return readTrimmedLine();
    }

    private Optional<String> promptForPassword() {
        return passwordPrompt.readPassword(PASSWORD_PROMPT);
    }

    /** Has no trim, so that the password stays as typed. */
    private Optional<String> readPasswordFromInput(String prompt) {
        output.println(prompt);
        return readRawLine();
    }

    private void handleOption(String option) {
        switch (option) {
            case LIST_LAUNCHES_OPTION -> listLaunches();
            case REGISTER_OPTION -> register();
            case LOG_IN_OPTION -> logIn();
            case LOG_OUT_OPTION -> logOut();
            case BOOK_SEATS_OPTION -> bookingFlow.run();
            case LIST_BOOKINGS_OPTION -> bookingRetrievalFlow.listBookings();
            case VIEW_BOOKING_OPTION -> bookingRetrievalFlow.viewBooking();
            default -> output.println("Unknown option. Select one of the options in the menu.");
        }
    }

    private void listLaunches() {
        try {
            showLaunches(launchService.listOpenLaunches());
        } catch (ApiFailure failure) {
            showFailure(failure);
        }
    }

    private void showLaunches(List<Launch> launches) {
        if (launches.isEmpty()) {
            output.println(NO_OPEN_LAUNCHES_MESSAGE);
            return;
        }
        launches.forEach(this::showLaunch);
    }

    private void showLaunch(Launch launch) {
        output.println();
        output.println("Name: " + launch.name());
        output.println("Price: " + launch.price().toPlainString());
        output.println("Date: " + LaunchDateFormatter.format(launch.date()));
        output.println("Duration: " + launch.duration());
        output.println("Location: " + launch.location());
        output.println("Minimum participants: " + launch.minParticipants());
        output.println("Maximum participants: " + launch.maxParticipants());
        output.println("Status: " + launch.status());
    }

    private void register() {
        Optional<String> username = promptFor("Username:");
        Optional<String> email = username.flatMap(value -> promptFor(EMAIL_PROMPT));
        Optional<String> password = email.flatMap(value -> promptForPassword());
        Optional<String> termsAnswer = password.flatMap(value -> promptFor("Do you accept the terms? (y/n):"));
        if (termsAnswer.isEmpty()) {
            return;
        }
        if (!isYes(termsAnswer.get())) {
            output.println("The terms were not accepted, so you were not registered.");
            return;
        }
        sendRegistration(username.get(), email.get(), password.get());
    }

    private static boolean isYes(String answer) {
        return YES_ANSWERS.contains(answer.toLowerCase(Locale.ROOT));
    }

    private void sendRegistration(String username, String firstEmail, String password) {
        Optional<String> email = Optional.of(firstEmail);
        while (email.isPresent()) {
            email = tryRegistration(username, email.get(), password);
        }
    }

    /** The result is the email to try next. An empty one means that the option has ended. */
    private Optional<String> tryRegistration(String username, String email, String password) {
        try {
            showLoggedIn(sessionService.register(username, email, password));
        } catch (ApiErrorFailure failure) {
            showFailure(failure);
            return hasEmailError(failure.apiError()) ? promptFor("Enter another email:") : Optional.empty();
        } catch (ApiFailure failure) {
            showFailure(failure);
        } catch (SessionFileFailure failure) {
            showSessionNotSaved();
        }
        return Optional.empty();
    }

    private static boolean hasEmailError(ApiError apiError) {
        return apiError.fieldErrors().stream().anyMatch(error -> error.field().equals(EMAIL_FIELD));
    }

    private void showSessionNotSaved() {
        output.println("The session could not be saved.");
    }

    private void logIn() {
        Optional<String> email = promptFor(EMAIL_PROMPT);
        Optional<String> password = email.flatMap(value -> promptForPassword());
        if (password.isPresent()) {
            sendLogin(email.get(), password.get());
        }
    }

    private void sendLogin(String email, String password) {
        try {
            showLoggedIn(sessionService.login(email, password));
        } catch (InvalidCredentialsFailure failure) {
            showFailure(failure);
            offerToRegister();
        } catch (ApiFailure failure) {
            showFailure(failure);
        } catch (SessionFileFailure failure) {
            showSessionNotSaved();
        }
    }

    private void offerToRegister() {
        promptFor("Do you want to register? (y/n):")
                .filter(ConsoleMenu::isYes)
                .ifPresent(answer -> register());
    }

    private void logOut() {
        try {
            sessionService.logout();
            output.println("Logged out.");
        } catch (SessionFileFailure failure) {
            output.println("The session file could not be deleted.");
        }
    }

    /** Package-private so that the booking flows of this layer catch {@link ApiFailure} and call it. */
    void showFailure(ApiFailure failure) {
        switch (failure) {
            case ApiNotReachableFailure notReachable ->
                    output.println("API not reachable at " + notReachable.url());
            case UnauthorizedFailure unauthorized -> showApiError(unauthorized.apiError());
            case ApiErrorFailure apiErrorFailure -> showApiError(apiErrorFailure.apiError());
            case InvalidCredentialsFailure invalidCredentials ->
                    output.println("The email or password was not accepted.");
            case SessionRejectedFailure sessionRejected ->
                    output.println("Your session is no longer valid. Please register again.");
            case NoSessionFailure noSession -> output.println("Register or log in first.");
            case LaunchNotBookableFailure notBookable ->
                    output.println("This launch is not open for booking.");
        }
    }

    private void showApiError(ApiError apiError) {
        output.println(apiError.message());
        apiError.fieldErrors().forEach(error -> output.println(error.field() + ": " + error.message()));
    }
}
