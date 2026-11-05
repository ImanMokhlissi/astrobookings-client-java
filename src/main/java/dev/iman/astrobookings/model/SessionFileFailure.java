package dev.iman.astrobookings.model;

/** The session file could not be read, written or deleted. No API call is involved. */
public final class SessionFileFailure extends RuntimeException {

    public SessionFileFailure(String message) {
        super(message);
    }

    public SessionFileFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
