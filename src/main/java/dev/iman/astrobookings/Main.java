package dev.iman.astrobookings;

import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.cli.ConsoleMenu;
import dev.iman.astrobookings.cli.PasswordPrompt;
import dev.iman.astrobookings.cli.StartupErrorView;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchService;
import dev.iman.astrobookings.service.SeatsCheck;
import dev.iman.astrobookings.service.SessionService;
import dev.iman.astrobookings.session.SessionStore;
import dev.iman.astrobookings.web.WebServer;
import dev.iman.astrobookings.web.WebServerStartFailure;
import java.io.Console;
import java.io.IOError;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Entry point of the client, outside the layers. */
public final class Main {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_STARTUP_FAILURE = 1;
    private static final String WEB_ARGUMENT = "--web";
    private static final String API_URL_VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final String WEB_PORT_VARIABLE = "ASTROBOOKINGS_WEB_PORT";
    private static final int DEFAULT_WEB_PORT = 8080;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int NO_PORT = -1;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private record Services(LaunchService launches, SessionService sessions, BookingService bookings) {
    }

    private Main() {
    }

    /** After a web start it just returns, and the server's own thread keeps the process alive (ADR-16). */
    public static void main(String[] args) {
        if (!isWebMode(args)) {
            System.exit(run(System.getenv(), System.in, System.out, realSessionFile(), System.console()));
        }
        if (startWeb(System.getenv(), System.out, realSessionFile()).isEmpty()) {
            System.exit(EXIT_CODE_STARTUP_FAILURE);
        }
    }

    static boolean isWebMode(String[] args) {
        return List.of(args).contains(WEB_ARGUMENT);
    }

    /** An empty result means that the error has been shown and nothing is running. */
    static Optional<WebServer> startWeb(Map<String, String> environment, PrintStream output, Path sessionFile) {
        StartupErrorView startupView = new StartupErrorView(output);
        String apiUrl = readApiUrl(environment);
        if (!isValidApiUrl(apiUrl)) {
            startupView.showInvalidUrl(apiUrl);
            return Optional.empty();
        }
        Optional<Integer> port = readWebPort(environment);
        if (port.isEmpty()) {
            startupView.showInvalidWebPort(environment.get(WEB_PORT_VARIABLE));
            return Optional.empty();
        }
        return startServer(buildServices(apiUrl, sessionFile), port.get(), startupView);
    }

    private static Optional<WebServer> startServer(Services services, int port, StartupErrorView startupView) {
        try {
            WebServer server = WebServer.start(services.launches(), services.sessions(), services.bookings(), port);
            startupView.showWebAddress(server.port());
            return Optional.of(server);
        } catch (WebServerStartFailure failure) {
            startupView.showWebPortUnavailable(port);
            return Optional.empty();
        }
    }

    /** Unset or blank means the default. An empty result means that the configured port is invalid. */
    static Optional<Integer> readWebPort(Map<String, String> environment) {
        String configuredPort = environment.get(WEB_PORT_VARIABLE);
        if (configuredPort == null || configuredPort.isBlank()) {
            return Optional.of(DEFAULT_WEB_PORT);
        }
        return SeatsCheck.readWholeNumber(configuredPort)
                .filter(Main::isValidPort)
                .map(BigInteger::intValueExact);
    }

    private static boolean isValidPort(BigInteger port) {
        return port.compareTo(BigInteger.valueOf(MIN_PORT)) >= 0 && port.compareTo(BigInteger.valueOf(MAX_PORT)) <= 0;
    }

    static int run(Map<String, String> environment, InputStream input, PrintStream output, Path sessionFile) {
        return run(environment, input, output, sessionFile, null);
    }

    /** The console is null when there is none, and then passwords come from the input. */
    static int run(
            Map<String, String> environment, InputStream input, PrintStream output, Path sessionFile, Console console) {
        String apiUrl = readApiUrl(environment);
        if (!isValidApiUrl(apiUrl)) {
            new StartupErrorView(output).showInvalidUrl(apiUrl);
            return EXIT_CODE_STARTUP_FAILURE;
        }
        buildMenu(apiUrl, input, output, sessionFile, console).run();
        return EXIT_CODE_SUCCESS;
    }

    private static Path realSessionFile() {
        return sessionFileUnder(Path.of(System.getProperty("user.home")));
    }

    static Path sessionFileUnder(Path homeFolder) {
        return homeFolder.resolve(".astrobookings").resolve("session.json");
    }

    static String readApiUrl(Map<String, String> environment) {
        String configuredUrl = environment.get(API_URL_VARIABLE);
        return configuredUrl == null || configuredUrl.isBlank() ? DEFAULT_API_URL : configuredUrl;
    }

    private static boolean isValidApiUrl(String apiUrl) {
        try {
            URI uri = new URI(apiUrl);
            return hasAllowedScheme(uri) && uri.getHost() != null && hasValidPort(uri);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean hasAllowedScheme(URI uri) {
        return uri.getScheme() != null && ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT));
    }

    private static boolean hasValidPort(URI uri) {
        int port = uri.getPort();
        return port == NO_PORT || (port >= MIN_PORT && port <= MAX_PORT);
    }

    private static ConsoleMenu buildMenu(
            String apiUrl, InputStream input, PrintStream output, Path sessionFile, Console console) {
        Services services = buildServices(apiUrl, sessionFile);
        if (console == null) {
            return new ConsoleMenu(services.launches(), services.sessions(), services.bookings(), input, output);
        }
        return new ConsoleMenu(
                services.launches(),
                services.sessions(),
                services.bookings(),
                input,
                output,
                passwordPromptOver(console));
    }

    private static Services buildServices(String apiUrl, Path sessionFile) {
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), apiUrl, REQUEST_TIMEOUT);
        LaunchService launchService = new LaunchService(apiClient);
        SessionService sessionService = new SessionService(apiClient, new SessionStore(sessionFile));
        BookingService bookingService = new BookingService(apiClient, launchService, sessionService);
        return new Services(launchService, sessionService, bookingService);
    }

    private static PasswordPrompt passwordPromptOver(Console console) {
        return prompt -> readPassword(console, prompt);
    }

    /** A console that can no longer be read counts as the end of the input. */
    private static Optional<String> readPassword(Console console, String prompt) {
        try {
            return Optional.ofNullable(console.readPassword("%s ", prompt)).map(String::new);
        } catch (IOError e) {
            return Optional.empty();
        }
    }
}
