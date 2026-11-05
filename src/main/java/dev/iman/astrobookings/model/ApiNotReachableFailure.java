package dev.iman.astrobookings.model;

/** The API could not be reached or the call timed out. */
public final class ApiNotReachableFailure extends ApiFailure {

    private final String url;

    public ApiNotReachableFailure(String url, Throwable cause) {
        super("API not reachable at " + url, cause);
        this.url = url;
    }

    /** The URL text as configured. */
    public String url() {
        return url;
    }
}
