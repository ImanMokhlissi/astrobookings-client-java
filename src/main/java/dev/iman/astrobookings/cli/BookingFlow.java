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

/** The booking option of the menu: choose a launch by its number, enter the seats and confirm. */
final class BookingFlow {

    private static final int PAYMENT_REJECTED_STATUS = 402;

    private final BookingService bookingService;
    private final NumberPrompt numberPrompt;
    private final PrintStream output;
    private final Consumer<ApiFailure> showFailure;

    BookingFlow(
            BookingService bookingService,
            NumberPrompt numberPrompt,
            PrintStream output,
            Consumer<ApiFailure> showFailure) {
        this.bookingService = bookingService;
        this.numberPrompt = numberPrompt;
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
            output.println(ConsoleMenu.NO_OPEN_LAUNCHES_MESSAGE);
            return;
        }
        numberPrompt.showNumbered(launches, BookingFlow::describe);
        chooseLaunch(launches).ifPresent(this::askForSeats);
    }

    private static String describe(Launch launch) {
        return launch.name()
                + " - " + LaunchDateFormatter.format(launch.date())
                + " - Price: " + launch.price().toPlainString();
    }

    private Optional<Launch> chooseLaunch(List<Launch> launches) {
        return numberPrompt.chooseFromList(
                "Launch number (0 to cancel):", launches, "Enter a launch number from the list.");
    }

    private void askForSeats(Launch launch) {
        numberPrompt
                .promptForNumber(
                        "Seats (0 to cancel):",
                        number -> true,
                        "The seats must be a whole number of at least 1.")
                .ifPresent(seats -> book(launch, seats));
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
