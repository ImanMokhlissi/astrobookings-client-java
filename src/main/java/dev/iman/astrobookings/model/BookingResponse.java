package dev.iman.astrobookings.model;

/**
 * A booking as the API sends it. Only {@code api} reads this DTO. The participants are an
 * {@code Integer}, so a missing value reads as null and not as 0.
 */
public record BookingResponse(
        String id,
        String activityId,
        String userId,
        Integer participants,
        String createdAt,
        String paymentId,
        String paymentStatus,
        BookingActivityResponse activity) {
}
