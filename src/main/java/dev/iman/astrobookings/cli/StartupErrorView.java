package dev.iman.astrobookings.cli;

import java.io.PrintStream;

/**
 * Shows on the terminal what comes before any menu or browser request: the startup errors and the
 * address of the running web server.
 */
public final class StartupErrorView {

    private final PrintStream output;

    public StartupErrorView(PrintStream output) {
        this.output = output;
    }

    public void showInvalidUrl(String configuredUrl) {
        output.println("Invalid API URL in ASTROBOOKINGS_API_URL: " + configuredUrl);
    }

    public void showInvalidWebPort(String configuredPort) {
        output.println("Invalid port in ASTROBOOKINGS_WEB_PORT: " + configuredPort + ". Use a number from 1 to 65535.");
    }

    public void showWebPortUnavailable(int port) {
        output.println("The port " + port + " is not available. Set another one in ASTROBOOKINGS_WEB_PORT.");
    }

    public void showWebAddress(int port) {
        output.println("AstroBookings is running at http://127.0.0.1:" + port + "/");
    }
}
