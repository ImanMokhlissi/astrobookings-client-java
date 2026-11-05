package example;

import example.ExampleApiClient.ApiErrorException;
import example.ExampleApiClient.ClientException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** The only layer that reads or prints. The streams are injected, so tests can drive it. */
public final class ExampleMenu {

    private static final int CANCEL = 0;

    private final ExampleService service;
    private final BufferedReader input;
    private final PrintStream output;

    public ExampleMenu(ExampleService service, InputStream input, PrintStream output) {
        this.service = service;
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
    }

    /** Runs until the user exits or the input ends. */
    public void run() {
        while (true) {
            output.println("1. Choose an item");
            output.println("0. Exit");
            Optional<String> option = readLine();
            if (option.isEmpty() || option.get().equals("0")) {
                return;
            }
            handleOption(option.get());
        }
    }

    private void handleOption(String option) {
        if (!option.equals("1")) {
            output.println("Unknown option: " + option);
            return;
        }
        try {
            chooseItem();
        } catch (ClientException failure) {
            showFailure(failure);
        }
    }

    private void chooseItem() {
        List<ExampleRecord> items = service.listOpenItems();
        if (items.isEmpty()) {
            output.println("No items available.");
            return;
        }
        selectFrom(items).ifPresent(item -> output.println("You chose: " + item.name()));
    }

    /** Asks until the entry is a number in the list. An empty result means 0 or the input's end. */
    private Optional<ExampleRecord> selectFrom(List<ExampleRecord> items) {
        for (int i = 0; i < items.size(); i++) {
            output.println((i + 1) + ". " + items.get(i).name());
        }
        while (true) {
            output.println("Enter a number (0 to cancel):");
            Optional<String> entry = readLine();
            if (entry.isEmpty()) {
                return Optional.empty();
            }
            OptionalInt number = parseNumber(entry.get());
            if (number.isPresent() && number.getAsInt() == CANCEL) {
                return Optional.empty();
            }
            if (number.isPresent() && number.getAsInt() <= items.size() && number.getAsInt() > 0) {
                return Optional.of(items.get(number.getAsInt() - 1));
            }
            output.println("Invalid number: " + entry.get());
        }
    }

    private OptionalInt parseNumber(String entry) {
        try {
            return OptionalInt.of(Integer.parseInt(entry));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private void showFailure(ClientException failure) {
        output.println(failure.getMessage());
        if (failure instanceof ApiErrorException apiError) {
            apiError.fieldErrors().forEach(e -> output.println(e.field() + ": " + e.message()));
        }
    }

    private Optional<String> readLine() {
        try {
            return Optional.ofNullable(input.readLine()).map(String::trim);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
