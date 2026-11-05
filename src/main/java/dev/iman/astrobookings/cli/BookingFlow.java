package dev.iman.astrobookings.cli;

import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.service.BookingService;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** The booking option of the menu: choose a launch by its number, enter the seats and confirm. */
final class BookingFlow {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]+");
    private static final int PAYMENT_REJECTED_STATUS = 402;

    private final BookingService bookingService;
    private final Function<String, Optional<String>> promptFor;
    private final PrintStream output;
    private final Consumer<ApiFailure> showFailure;

    BookingFlow(
            BookingService bookingService,
            Function<String, Optional<String>> promptFor,
            PrintStream output,
            Consumer<ApiFailure> showFailure) {
        this.bookingService = bookingService;
        this.promptFor = promptFor;
        this.output = output;
        this.showFailure = showFailure;
    }

    void run() {
        try {
            offerLaunches(bookingService.listBookableLaunches());
        } catch (ApiFailure failure) {
            showFailure.accept(failure);
        }
    }

    private void offerLaunches(List<Launch> launches) {
        if (launches.isEmpty()) {
            output.println("No launches open for booking.");
            return;
        }
        showNumbered(launches);
        chooseLaunch(launches).ifPresent(this::askForSeats);
    }

    private void showNumbered(List<Launch> launches) {
        for (int index = 0; index < launches.size(); index++) {
            output.println((index + 1) + ". " + describe(launches.get(index)));
        }
    }

    private static String describe(Launch launch) {
        return launch.name()
                + " - " + LaunchDateFormatter.format(launch.date())
                + " - Price: " + launch.price().toPlainString();
    }

    private Optional<Launch> chooseLaunch(List<Launch> launches) {
        BigInteger lastNumber = BigInteger.valueOf(launches.size());
        return promptForNumber(
                        "Launch number (0 to cancel):",
                        number -> number.compareTo(lastNumber) <= 0,
                        "Enter a launch number from the list.")
                .map(number -> launches.get(number.intValueExact() - 1));
    }

    private void askForSeats(Launch launch) {
        promptForNumber(
                        "Seats (0 to cancel):",
                        number -> true,
                        "The seats must be a whole number of at least 1.")
                .ifPresent(seats -> book(launch, seats));
    }

    /** Asks until the entry is accepted. An empty result means that it was 0 or that the input ended. */
    private Optional<BigInteger> promptForNumber(
            String prompt, Predicate<BigInteger> isAccepted, String invalidEntryMessage) {
        Optional<String> entry = promptFor.apply(prompt);
        while (entry.isPresent()) {
            Optional<BigInteger> number = readWholeNumber(entry.get());
            if (number.filter(BigInteger.ZERO::equals).isPresent()) {
                return Optional.empty();
            }
            if (number.filter(isAccepted).isPresent()) {
                return number;
            }
            output.println(invalidEntryMessage);
            entry = promptFor.apply(prompt);
        }
        return Optional.empty();
    }

    /** Only ASCII digits count. {@code BigInteger} takes a number of any length. */
    private static Optional<BigInteger> readWholeNumber(String entry) {
        return WHOLE_NUMBER.matcher(entry).matches() ? Optional.of(new BigInteger(entry)) : Optional.empty();
    }

    private void book(Launch launch, BigInteger seats) {
        try {
            showConfirmation(launch, bookingService.book(launch, seats));
        } catch (ApiErrorFailure failure) {
            showFailure.accept(failure);
            if (failure.apiError().status() == PAYMENT_REJECTED_STATUS) {
                output.println("No booking was created.");
            }
        }
    }

    /** The launch is the one the user chose, because a created booking has no launch data. */
    private void showConfirmation(Launch launch, Booking booking) {
        output.println("Booking created.");
        output.println("Booking id: " + booking.id());
        output.println("Launch: " + launch.name());
        output.println("Seats: " + booking.participants());
        output.println("Payment status: " + booking.paymentStatus());
    }
}
