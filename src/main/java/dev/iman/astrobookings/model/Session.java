package dev.iman.astrobookings.model;

/**
 * The saved session. It has no password, and no expiry because the API decides it. Every field
 * must have text.
 */
public record Session(String accessToken, String username, String email) {

    public Session {
        requireText(accessToken, "accessToken");
        requireText(username, "username");
        requireText(email, "email");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must have text");
        }
    }
}
