package dev.iman.astrobookings.model;

/** The launch failed the status allow-list, so no booking request was sent to the API. */
public final class LaunchNotBookableFailure extends ApiFailure {

    public LaunchNotBookableFailure() {
        super("The launch is not open for booking.");
    }
}
