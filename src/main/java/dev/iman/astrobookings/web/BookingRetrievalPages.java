package dev.iman.astrobookings.web;

import static dev.iman.astrobookings.web.HtmlEscaper.escape;

import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.LaunchSummary;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchDateFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** My bookings and the detail of one booking. */
final class BookingRetrievalPages {

    private static final String LIST_HEADING = "My bookings";
    private static final String DETAIL_HEADING = "Booking";
    private static final String NO_PAYMENT_ID = "none";

    private final BookingService bookingService;
    private final PageLayout layout;

    BookingRetrievalPages(BookingService bookingService, PageLayout layout) {
        this.bookingService = bookingService;
        this.layout = layout;
    }

    WebResponse showBookings() {
        return layout.page(
                LIST_HEADING, FailureView.contentOrFailure(() -> bookingList(bookingService.listBookings())));
    }

    private static String bookingList(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return HtmlFields.message("You have no bookings.");
        }
        return bookings.stream().map(BookingRetrievalPages::bookingSummary).collect(Collectors.joining());
    }

    private static String bookingSummary(Booking booking) {
        return HtmlFields.list(
                linkToDetail(booking),
                HtmlFields.field("Launch", booking.launch().name()),
                HtmlFields.field("Seats", String.valueOf(booking.participants())),
                HtmlFields.field("Payment status", booking.paymentStatus()));
    }

    private static String linkToDetail(Booking booking) {
        String href = escape(BookingIdPath.pathOf(booking.id()));
        return "<li>Booking id: <a href=\"" + href + "\">" + escape(booking.id()) + "</a></li>\n";
    }

    WebResponse showBooking(String bookingId) {
        return layout.page(
                DETAIL_HEADING,
                FailureView.contentOrFailure(() -> bookingDetail(bookingService.getBooking(bookingId))));
    }

    private static String bookingDetail(Booking booking) {
        LaunchSummary launch = booking.launch();
        return HtmlFields.list(
                HtmlFields.field("Booking id", booking.id()),
                HtmlFields.field("Seats", String.valueOf(booking.participants())),
                HtmlFields.field("Created", LaunchDateFormatter.format(booking.createdAt())),
                HtmlFields.field("Payment id", Optional.ofNullable(booking.paymentId()).orElse(NO_PAYMENT_ID)),
                HtmlFields.field("Payment status", booking.paymentStatus()),
                HtmlFields.field("Launch", launch.name()),
                HtmlFields.field("Price", launch.price().toPlainString()),
                HtmlFields.field("Date", LaunchDateFormatter.format(launch.date())),
                HtmlFields.field("Duration", launch.duration().toPlainString()),
                HtmlFields.field("Location", launch.location()),
                HtmlFields.field("Launch status", launch.status()));
    }
}
