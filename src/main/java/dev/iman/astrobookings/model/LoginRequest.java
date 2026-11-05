package dev.iman.astrobookings.model;

/** The body of {@code POST /login}. Only {@code api} writes this DTO. */
public record LoginRequest(String email, String password) {
}
