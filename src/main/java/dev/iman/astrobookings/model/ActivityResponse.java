package dev.iman.astrobookings.model;

import java.math.BigDecimal;

/**
 * An activity as the API sends it. Only {@code api} reads this DTO. The whole numbers are
 * {@code Integer}, so a missing one reads as null and not as 0.
 */
public record ActivityResponse(
        String id,
        String name,
        String slug,
        BigDecimal price,
        String date,
        Integer duration,
        String location,
        Integer minParticipants,
        Integer maxParticipants,
        String status) {
}
