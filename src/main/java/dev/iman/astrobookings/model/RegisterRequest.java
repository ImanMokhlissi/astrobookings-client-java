package dev.iman.astrobookings.model;

/** The body of {@code POST /users}. Only {@code api} writes this DTO. */
public record RegisterRequest(String username, String email, String password, boolean terms) {
}
