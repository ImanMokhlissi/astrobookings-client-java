package dev.iman.astrobookings.model;

/** One entry of the {@code errors} list in the API's error format. Only {@code api} reads it. */
public record FieldErrorResponse(String field, String message) {
}
