package dev.iman.astrobookings.model;

/**
 * Every way an API call can fail. The compiler knows all the types. {@code api} produces the
 * first three, and {@code service} raises the other two when it gives a 401 its meaning.
 */
public abstract sealed class ApiFailure extends RuntimeException
        permits ApiNotReachableFailure,
                UnauthorizedFailure,
                ApiErrorFailure,
                InvalidCredentialsFailure,
                SessionRejectedFailure {

    ApiFailure(String message) {
        super(message);
    }

    ApiFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
