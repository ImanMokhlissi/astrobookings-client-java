package dev.iman.astrobookings.model;

/** The API responded to a login with a 401. */
public final class InvalidCredentialsFailure extends ApiFailure {

    public InvalidCredentialsFailure(UnauthorizedFailure cause) {
        super("The email or password was not accepted.", cause);
    }
}
