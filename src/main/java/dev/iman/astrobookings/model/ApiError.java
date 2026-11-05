package dev.iman.astrobookings.model;

import java.util.List;
import java.util.Objects;

/** A failed API response. The list of field errors may be empty, and is never null. */
public record ApiError(int status, String message, List<FieldError> fieldErrors) {

    public ApiError {
        Objects.requireNonNull(message, "message");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
