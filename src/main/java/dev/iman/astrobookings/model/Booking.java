package dev.iman.astrobookings.model;

import java.util.Objects;

/**
 * Seats booked by a user. The payment status stays as the text the API sends. {@code launch} is
 * null on a newly created booking, which has no launch data, and {@code paymentId} is null when the
 * API leaves it out, because the API declares it optional.
 */
public record Booking(
        String id,
        String launchId,
        String userId,
        int participants,
        String createdAt,
        String paymentId,
        String paymentStatus,
        LaunchSummary launch) {

    public Booking {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(launchId, "launchId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(paymentStatus, "paymentStatus");
    }
}
