package dev.iman.astrobookings.model;

/** The response to registration and login. Only {@code api} reads this DTO. */
public record AuthResponse(User user, String accessToken) {
}
