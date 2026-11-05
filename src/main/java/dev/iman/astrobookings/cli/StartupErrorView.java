package dev.iman.astrobookings.cli;

import java.io.PrintStream;

/** Shows the errors that come before the layers are wired, when the menu cannot exist yet. */
public final class StartupErrorView {

    private final PrintStream output;

    public StartupErrorView(PrintStream output) {
        this.output = output;
    }

    public void showInvalidUrl(String configuredUrl) {
        output.println("Invalid API URL in ASTROBOOKINGS_API_URL: " + configuredUrl);
    }
}
