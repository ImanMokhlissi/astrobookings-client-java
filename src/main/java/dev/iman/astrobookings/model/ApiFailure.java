package dev.iman.astrobookings.model;

/**
 * Every way an API call can fail. The compiler knows all the types. {@code api} produces the
 * first three. {@code service} raises the next two when it gives a 401 its meaning, and the last
 * two when one of its rules stops a booking before any request is sent.
 */
public abstract sealed class ApiFailure extends RuntimeException
        permits ApiNotReachableFailure,
                UnauthorizedFailure,
                ApiErrorFailure,
                InvalidCredentialsFailure,
                SessionRejectedFailure,
                NoSessionFailure,
                LaunchNotBookableFailure {

    ApiFailure(String message) {
        super(message);
    }

    ApiFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
