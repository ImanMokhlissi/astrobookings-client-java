package dev.iman.astrobookings.model;

import java.util.Objects;

/** One invalid field in a failed API response. */
public record FieldError(String field, String message) {

    public FieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
