package dev.iman.astrobookings.model;

import java.math.BigDecimal;
import java.util.Objects;

/** A rocket launch as the customer sees it. Status and date stay as the text the API sends. */
public record Launch(
        String id,
        String name,
        String slug,
        BigDecimal price,
        String date,
        int duration,
        String location,
        int minParticipants,
        int maxParticipants,
        String status) {

    public Launch {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(status, "status");
    }
}
