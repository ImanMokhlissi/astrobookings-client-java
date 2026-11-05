package dev.iman.astrobookings.model;

import java.math.BigDecimal;

/** The activity embedded in a booking, as the API sends it. Only {@code api} reads this DTO. */
public record BookingActivityResponse(
        String name,
        String slug,
        BigDecimal price,
        String date,
        BigDecimal duration,
        String location,
        String status) {
}
