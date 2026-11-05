package dev.iman.astrobookings.cli;

import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.LaunchSummary;
import dev.iman.astrobookings.service.BookingService;
import java.io.PrintStream;
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
        runShowingFailures(this::showFreshList);
    }

    void viewBooking() {
        runShowingFailures(this::viewFromFreshList);
    }

    private void runShowingFailures(Runnable option) {
        try {
            option.run();
        } catch (ApiFailure failure) {
            showFailure.accept(failure);
        }
    }

    private void viewFromFreshList() {
        List<Booking> bookings = showFreshList();
        if (!bookings.isEmpty()) {
            chooseBooking(bookings).ifPresent(this::showChosenBooking);
        }
    }

    private List<Booking> showFreshList() {
        List<Booking> bookings = bookingService.listBookings();
        if (bookings.isEmpty()) {
            output.println("You have no bookings.");
        }
        numberPrompt.showNumbered(bookings, BookingRetrievalFlow::describe);
        return bookings;
    }

    private static String describe(Booking booking) {
        return booking.id()
                + " - " + booking.launch().name()
                + " - Seats: " + booking.participants()
                + " - Payment status: " + booking.paymentStatus();
    }

    private Optional<Booking> chooseBooking(List<Booking> bookings) {
        return numberPrompt.chooseFromList(
                "Booking number (0 to cancel):", bookings, "Enter a booking number from the list.");
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
