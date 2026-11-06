package dev.iman.astrobookings.web;

/** The server could not bind its address and port. */
public final class WebServerStartFailure extends RuntimeException {

    public WebServerStartFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
