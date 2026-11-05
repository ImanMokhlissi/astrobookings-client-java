package dev.iman.astrobookings;

import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.cli.ConsoleMenu;
import dev.iman.astrobookings.cli.StartupErrorView;
import dev.iman.astrobookings.service.LaunchService;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Entry point of the client, outside the layers. */
public final class Main {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_INVALID_URL = 1;
    private static final String API_URL_VARIABLE = "ASTROBOOKINGS_API_URL";
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int NO_PORT = -1;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(System.getenv(), System.in, System.out));
    }

    static int run(Map<String, String> environment, InputStream input, PrintStream output) {
        String apiUrl = readApiUrl(environment);
        if (!isValidApiUrl(apiUrl)) {
            new StartupErrorView(output).showInvalidUrl(apiUrl);
            return EXIT_CODE_INVALID_URL;
        }
        buildMenu(apiUrl, input, output).run();
        return EXIT_CODE_SUCCESS;
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

    private static ConsoleMenu buildMenu(String apiUrl, InputStream input, PrintStream output) {
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), apiUrl, REQUEST_TIMEOUT);
        return new ConsoleMenu(new LaunchService(apiClient), input, output);
    }
}
