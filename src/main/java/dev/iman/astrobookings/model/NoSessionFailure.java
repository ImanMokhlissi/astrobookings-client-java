package dev.iman.astrobookings.model;

/** A use case that needs a session was started with none, so no call was made to the API. */
public final class NoSessionFailure extends ApiFailure {

    public NoSessionFailure() {
        super("There is no session.");
    }
}
