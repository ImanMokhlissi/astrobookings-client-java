package dev.iman.astrobookings.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The launch data embedded in a booking. Date and status stay as the text the API sends, and the
 * duration is a {@code BigDecimal} so that a fractional value is shown with no rounding.
 */
public record LaunchSummary(
        String name,
        String slug,
        BigDecimal price,
        String date,
        BigDecimal duration,
        String location,
        String status) {

    public LaunchSummary {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(status, "status");
    }
}
