package dev.iman.astrobookings.service;

import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.LaunchNotBookableFailure;
import dev.iman.astrobookings.model.NoSessionFailure;
import dev.iman.astrobookings.model.Session;
import java.math.BigInteger;
import java.util.List;

/**
 * The use cases that book seats and retrieve bookings. They all need a session, and booking also
 * applies the allow-list first.
 */
public final class BookingService {

    private final ApiClient apiClient;
    private final LaunchService launchService;
    private final SessionService sessionService;

    public BookingService(ApiClient apiClient, LaunchService launchService, SessionService sessionService) {
        this.apiClient = apiClient;
        this.launchService = launchService;
        this.sessionService = sessionService;
    }

    /** A 401 here stays an API error and keeps the session, because the list is a public call. */
    public List<Launch> listBookableLaunches() {
        requireSession();
        return launchService.listOpenLaunches();
    }

    /** A 401 here ends the session, because this call sends the token. */
    public Booking book(Launch launch, BigInteger seats) {
        Session session = requireSession();
        if (!launchService.isOpenForBooking(launch)) {
            throw new LaunchNotBookableFailure();
        }
        return sessionService.runAuthenticated(
                () -> apiClient.createBooking(session.accessToken(), launch.id(), seats));
    }

    /** A 401 here ends the session, because this call sends the token. */
    public List<Booking> listBookings() {
        Session session = requireSession();
        return sessionService.runAuthenticated(() -> apiClient.fetchBookings(session.accessToken()));
    }

    /** A 401 here ends the session, because this call sends the token. */
    public Booking getBooking(String bookingId) {
        Session session = requireSession();
        return sessionService.runAuthenticated(() -> apiClient.fetchBooking(session.accessToken(), bookingId));
    }

    private Session requireSession() {
        return sessionService.currentSession().orElseThrow(NoSessionFailure::new);
    }
}
