package dev.iman.astrobookings.web;

import java.nio.charset.StandardCharsets;

/** One answer to a browser. An empty {@code location} means that it is not a redirect. */
record WebResponse(int status, String contentType, byte[] body, String location) {

    static final int OK_STATUS = 200;

    private static final int SEE_OTHER_STATUS = 303;
    private static final String NO_LOCATION = "";
    private static final String NO_CONTENT_TYPE = "";

    static WebResponse html(int status, String html) {
        return new WebResponse(
                status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), NO_LOCATION);
    }

    static WebResponse css(byte[] css) {
        return new WebResponse(OK_STATUS, "text/css; charset=utf-8", css, NO_LOCATION);
    }

    /** The location is a relative path, so that the browser stays on the host the user opened. */
    static WebResponse redirect(String location) {
        return new WebResponse(SEE_OTHER_STATUS, NO_CONTENT_TYPE, new byte[0], location);
    }

    boolean isRedirect() {
        return !location.isEmpty();
    }
}
