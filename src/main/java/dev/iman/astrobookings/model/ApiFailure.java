package dev.iman.astrobookings.model;

/** Every way an API call can fail. The compiler knows all the types. */
public abstract sealed class ApiFailure extends RuntimeException
        permits ApiNotReachableFailure, UnauthorizedFailure, ApiErrorFailure {

    ApiFailure(String message) {
        super(message);
    }

    ApiFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
