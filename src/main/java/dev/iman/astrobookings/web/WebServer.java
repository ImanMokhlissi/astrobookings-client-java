package dev.iman.astrobookings.web;

import com.sun.net.httpserver.HttpServer;
import dev.iman.astrobookings.model.SessionFileFailure;
import dev.iman.astrobookings.service.BookingService;
import dev.iman.astrobookings.service.LaunchService;
import dev.iman.astrobookings.service.SessionService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * The web interface, only on 127.0.0.1 because it acts with the saved token (ADR-17). The default
 * executor handles one request at a time, which keeps {@link SessionService} safe (ADR-14).
 */
public final class WebServer {

    private static final byte[] LOOPBACK_ADDRESS = {127, 0, 0, 1};
    private static final int DEFAULT_BACKLOG = 0;
    private static final int STOP_AT_ONCE = 0;

    private final HttpServer server;

    private WebServer(HttpServer server) {
        this.server = server;
    }

    /** Port 0 asks the system for any free port, and {@link #port()} tells which one it gave. */
    public static WebServer start(
            LaunchService launchService, SessionService sessionService, BookingService bookingService, int port) {
        HttpServer server = bind(port);
        boolean sessionFileUnreadable = !loadSavedSession(sessionService);
        PageLayout layout = new PageLayout(sessionService, sessionFileUnreadable);
        server.createContext(
                "/",
                new WebRouter(
                        new OriginCheck(server.getAddress().getPort()),
                        layout,
                        new LaunchesPage(launchService, layout),
                        new SessionPages(sessionService, layout),
                        new BookingPages(bookingService, sessionService, layout),
                        new BookingRetrievalPages(bookingService, layout)));
        server.start();
        return new WebServer(server);
    }

    private static HttpServer bind(int port) {
        try {
            InetAddress address = InetAddress.getByAddress(LOOPBACK_ADDRESS);
            return HttpServer.create(new InetSocketAddress(address, port), DEFAULT_BACKLOG);
        } catch (IOException e) {
            throw new WebServerStartFailure("The port could not be bound: " + port, e);
        }
    }

    /** The result says whether the file could be read. A file failure counts as no session. */
    private static boolean loadSavedSession(SessionService sessionService) {
        try {
            sessionService.loadSavedSession();
            return true;
        } catch (SessionFileFailure failure) {
            return false;
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(STOP_AT_ONCE);
    }
}
