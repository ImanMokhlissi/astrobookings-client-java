package dev.iman.astrobookings.model;

/** The person a session belongs to, as the API sends it. Only {@code api} reads this record. */
public record User(String username, String email) {
}
