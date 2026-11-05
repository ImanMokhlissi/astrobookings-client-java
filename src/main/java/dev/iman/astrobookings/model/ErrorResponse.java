package dev.iman.astrobookings.model;

import java.util.List;

/** The API's error format. Only {@code api} reads this DTO. */
public record ErrorResponse(String message, List<FieldErrorResponse> errors) {
}
