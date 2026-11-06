package dev.iman.astrobookings.web;

import static dev.iman.astrobookings.web.HtmlEscaper.escape;

import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.NoSessionFailure;
import dev.iman.astrobookings.model.SessionRejectedFailure;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchDateFormatter;
import dev.iman.astrobookings.service.SeatsCheck;
import dev.iman.astrobookings.service.SessionService;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** The booking form and its action. The launch id always goes through {@code service}'s fresh list. */
final class BookingPages {

    private static final String HEADING = "Book seats";
    private static final int PAYMENT_REJECTED_STATUS = 402;
    private static final String NO_FAILURE = "";
    private static final String NO_FORM = "";
    private static final String NOTHING_TYPED = "";
    private static final String INVALID_SEATS = HtmlFields.error("The seats must be a whole number of at least 1.");
    private static final String NO_BOOKING_CREATED = HtmlFields.error("No booking was created.");

    private final BookingService bookingService;
    private final SessionService sessionService;
    private final PageLayout layout;

    BookingPages(BookingService bookingService, SessionService sessionService, PageLayout layout) {
        this.bookingService = bookingService;
        this.sessionService = sessionService;
        this.layout = layout;
    }

    WebResponse showForm() {
        return layout.page(HEADING, freshForm());
    }

    private String freshForm() {
        try {
            return form(bookingService.listBookableLaunches(), NOTHING_TYPED, NOTHING_TYPED);
        } catch (ApiFailure failure) {
            return FailureView.html(failure);
        }
    }

    WebResponse book(FormData form) {
        if (sessionService.currentSession().isEmpty()) {
            return layout.page(HEADING, FailureView.html(new NoSessionFailure()));
        }
        String launchId = form.value("launchId");
        String typedSeats = form.value("seats");
        Optional<BigInteger> seats = SeatsCheck.readSeats(typedSeats.trim());
        if (seats.isEmpty()) {
            return formAgain(INVALID_SEATS, launchId, typedSeats);
        }
        return bookLaunch(launchId, seats.get(), typedSeats);
    }

    private WebResponse bookLaunch(String launchId, BigInteger seats, String typedSeats) {
        try {
            Booking booking = bookingService.book(launchId, seats);
            return WebResponse.redirect(BookingIdPath.pathOf(booking.id()));
        } catch (SessionRejectedFailure failure) {
            return layout.page(HEADING, FailureView.html(failure));
        } catch (ApiErrorFailure failure) {
            return formAgain(apiErrorWithConsequence(failure), launchId, typedSeats);
        } catch (ApiFailure failure) {
            return formAgain(FailureView.html(failure), launchId, typedSeats);
        }
    }

    private static String apiErrorWithConsequence(ApiErrorFailure failure) {
        boolean paymentRejected = failure.apiError().status() == PAYMENT_REJECTED_STATUS;
        return FailureView.html(failure) + (paymentRejected ? NO_BOOKING_CREATED : NO_FAILURE);
    }

    /** The failure stays on the page even when the fresh list also fails, with no form then. */
    private WebResponse formAgain(String failure, String launchId, String typedSeats) {
        String formAfterFailure = formFromFreshListOrNothing(launchId, typedSeats);
        return layout.page(HEADING, failure + formAfterFailure);
    }

    private String formFromFreshListOrNothing(String launchId, String typedSeats) {
        try {
            return form(bookingService.listBookableLaunches(), launchId, typedSeats);
        } catch (ApiFailure failure) {
            return NO_FORM;
        }
    }

    private static String form(List<Launch> launches, String chosenLaunchId, String typedSeats) {
        if (launches.isEmpty()) {
            return HtmlFields.message("No launches open for booking.");
        }
        String options = launches.stream()
                .map(launch -> option(launch, chosenLaunchId))
                .collect(Collectors.joining());
        return """
                <form method="post" action="/book">
                <label for="launchId">Launch</label>
                <select id="launchId" name="launchId">
                %s</select>
                <label for="seats">Seats</label>
                <input type="text" id="seats" name="seats" value="%s">
                <button type="submit">Book</button>
                </form>
                """.formatted(options, escape(typedSeats));
    }

    private static String option(Launch launch, String chosenLaunchId) {
        String selected = launch.id().equals(chosenLaunchId) ? " selected" : "";
        return "<option value=\"" + escape(launch.id()) + "\"" + selected + ">" + escape(describe(launch))
                + "</option>\n";
    }

    private static String describe(Launch launch) {
        return launch.name()
                + " - " + LaunchDateFormatter.format(launch.date())
                + " - Price: " + launch.price().toPlainString();
    }
}
