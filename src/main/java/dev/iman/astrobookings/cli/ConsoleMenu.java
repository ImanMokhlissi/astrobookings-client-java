package dev.iman.astrobookings.cli;

import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.ApiNotReachableFailure;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import dev.iman.astrobookings.service.LaunchService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** The interactive menu. It runs until the user exits or the input ends. */
public final class ConsoleMenu {

    private static final String LIST_LAUNCHES_OPTION = "1";
    private static final String EXIT_OPTION = "0";

    private final LaunchService launchService;
    private final BufferedReader input;
    private final PrintStream output;

    public ConsoleMenu(LaunchService launchService, InputStream input, PrintStream output) {
        this.launchService = launchService;
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
    }

    public void run() {
        while (true) {
            showMenu();
            Optional<String> option = readOption();
            if (option.isEmpty() || option.get().equals(EXIT_OPTION)) {
                return;
            }
            handleOption(option.get());
        }
    }

    private void showMenu() {
        output.println();
        output.println("AstroBookings");
        output.println(LIST_LAUNCHES_OPTION + ". List launches");
        output.println(EXIT_OPTION + ". Exit");
        output.println("Select an option:");
    }

    /** An empty result means that the input ended, which a read error also counts as. */
    private Optional<String> readOption() {
        try {
            return Optional.ofNullable(input.readLine()).map(String::trim);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void handleOption(String option) {
        if (option.equals(LIST_LAUNCHES_OPTION)) {
            listLaunches();
        } else {
            output.println("Unknown option. Select one of the options in the menu.");
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
            output.println("No launches open for booking.");
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

    private void showFailure(ApiFailure failure) {
        switch (failure) {
            case ApiNotReachableFailure notReachable ->
                    output.println("API not reachable at " + notReachable.url());
            case UnauthorizedFailure unauthorized -> showApiError(unauthorized.apiError());
            case ApiErrorFailure apiErrorFailure -> showApiError(apiErrorFailure.apiError());
        }
    }

    private void showApiError(ApiError apiError) {
        output.println(apiError.message());
        apiError.fieldErrors().forEach(error -> output.println(error.field() + ": " + error.message()));
    }
}
