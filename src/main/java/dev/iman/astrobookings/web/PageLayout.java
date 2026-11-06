package dev.iman.astrobookings.web;

import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.service.SessionService;

/** The frame of every page: navigation, who is logged in, the logout form and the CSS link. */
final class PageLayout {

    private static final int OK_STATUS = 200;

    private final SessionService sessionService;
    private boolean sessionFileUnreadable;

    PageLayout(SessionService sessionService, boolean sessionFileUnreadable) {
        this.sessionService = sessionService;
        this.sessionFileUnreadable = sessionFileUnreadable;
    }

    /** A saved session or a logout means that the unreadable file no longer matters. */
    void forgetUnreadableSessionFile() {
        sessionFileUnreadable = false;
    }

    WebResponse page(String heading, String content) {
        return page(OK_STATUS, heading, content);
    }

    /** Call it after the service call, so that the page shows the session as it is after that call. */
    WebResponse page(int status, String heading, String content) {
        return WebResponse.html(status, """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <title>AstroBookings: %1$s</title>
                <link rel="stylesheet" href="/style.css">
                </head>
                <body>
                <header>
                <p class="brand">AstroBookings</p>
                <nav>
                <a href="/">Launches</a>
                <a href="/register">Register</a>
                <a href="/login">Log in</a>
                <a href="/book">Book seats</a>
                <a href="/bookings">My bookings</a>
                </nav>
                %2$s
                </header>
                <main>
                <h1>%1$s</h1>
                %3$s
                </main>
                </body>
                </html>
                """.formatted(heading, sessionLines(), content));
    }

    private String sessionLines() {
        return sessionService.currentSession().map(PageLayout::loggedInLines).orElseGet(this::noSessionLines);
    }

    private static String loggedInLines(Session session) {
        String loggedIn = "Logged in as " + session.username() + " (" + session.email() + ")";
        return """
                <p class="session">%s</p>
                <form method="post" action="/logout"><button type="submit">Log out</button></form>
                """.formatted(HtmlEscaper.escape(loggedIn));
    }

    private String noSessionLines() {
        return sessionFileUnreadable
                ? "<p class=\"error\">The session file could not be read. Continuing with no session.</p>"
                : "";
    }
}
