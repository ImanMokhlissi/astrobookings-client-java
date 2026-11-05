package dev.iman.astrobookings.model;

import java.math.BigInteger;

/**
 * The body of {@code POST /bookings}. Only {@code api} writes this DTO. The participants are a
 * {@code BigInteger} because a very long whole number is still valid, and the API answers it.
 */
public record CreateBookingRequest(String activityId, BigInteger participants) {
}
