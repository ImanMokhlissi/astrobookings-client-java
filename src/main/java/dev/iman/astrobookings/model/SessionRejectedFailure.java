package dev.iman.astrobookings.model;

/**
 * The API responded to an authenticated call with a 401. It is separate from
 * {@link UnauthorizedFailure} because a 401 on a public call does not reject the session.
 */
public final class SessionRejectedFailure extends ApiFailure {

    public SessionRejectedFailure(UnauthorizedFailure cause) {
        super("The session is no longer valid.", cause);
    }
}
