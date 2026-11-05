package dev.iman.astrobookings.cli;

import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.LaunchSummary;
import dev.iman.astrobookings.service.BookingService;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** The options of the menu that list the user's bookings and show one, chosen by its number. */
final class BookingRetrievalFlow {

    private static final String NO_PAYMENT_ID = "none";

    private final BookingService bookingService;
    private final NumberPrompt numberPrompt;
    private final PrintStream output;
    private final Consumer<ApiFailure> showFailure;

    BookingRetrievalFlow(
            BookingService bookingService,
            NumberPrompt numberPrompt,
            PrintStream output,
            Consumer<ApiFailure> showFailure) {
        this.bookingService = bookingService;
        this.numberPrompt = numberPrompt;
        this.output = output;
        this.showFailure = showFailure;
    }

    void listBookings() {
        try {
            showNumbered(bookingService.listBookings());
        } catch (ApiFailure failure) {
            showFailure.accept(failure);
        }
    }

    void viewBooking() {
        try {
            List<Booking> bookings = bookingService.listBookings();
            showNumbered(bookings);
            if (!bookings.isEmpty()) {
                chooseBooking(bookings).ifPresent(this::showChosenBooking);
            }
        } catch (ApiFailure failure) {
            showFailure.accept(failure);
        }
    }

    private void showNumbered(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            output.println("You have no bookings.");
        }
        for (int index = 0; index < bookings.size(); index++) {
            output.println((index + 1) + ". " + describe(bookings.get(index)));
        }
    }

    private static String describe(Booking booking) {
        return booking.id()
                + " - " + booking.launch().name()
                + " - Seats: " + booking.participants()
                + " - Payment status: " + booking.paymentStatus();
    }

    private Optional<Booking> chooseBooking(List<Booking> bookings) {
        BigInteger lastNumber = BigInteger.valueOf(bookings.size());
        return numberPrompt
                .promptForNumber(
                        "Booking number (0 to cancel):",
                        number -> number.compareTo(lastNumber) <= 0,
                        "Enter a booking number from the list.")
                .map(number -> bookings.get(number.intValueExact() - 1));
    }

    private void showChosenBooking(Booking chosen) {
        showDetails(bookingService.getBooking(chosen.id()));
    }

    private void showDetails(Booking booking) {
        output.println("Booking id: " + booking.id());
        output.println("Seats: " + booking.participants());
        output.println("Created: " + LaunchDateFormatter.format(booking.createdAt()));
        output.println("Payment id: " + Optional.ofNullable(booking.paymentId()).orElse(NO_PAYMENT_ID));
        output.println("Payment status: " + booking.paymentStatus());
        showLaunch(booking.launch());
    }

    private void showLaunch(LaunchSummary launch) {
        output.println("Launch: " + launch.name());
        output.println("Price: " + launch.price().toPlainString());
        output.println("Date: " + LaunchDateFormatter.format(launch.date()));
        output.println("Duration: " + launch.duration().toPlainString());
        output.println("Location: " + launch.location());
        output.println("Launch status: " + launch.status());
    }
}
