package dev.iman.astrobookings.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/** Holds the routes. The check on the origin comes first, and nothing unexpected reaches the browser. */
final class WebRouter implements HttpHandler {

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String ORIGIN_HEADER = "Origin";
    private static final String STYLE_SHEET = "/web/style.css";
    private static final int FORBIDDEN_STATUS = 403;
    private static final int NOT_FOUND_STATUS = 404;
    private static final int METHOD_NOT_ALLOWED_STATUS = 405;
    private static final int SERVER_ERROR_STATUS = 500;
    private static final int NO_BODY = -1;

    private final OriginCheck originCheck;
    private final PageLayout layout;
    private final LaunchesPage launchesPage;
    private final SessionPages sessionPages;
    private final BookingPages bookingPages;
    private final BookingRetrievalPages bookingRetrievalPages;

    WebRouter(
            OriginCheck originCheck,
            PageLayout layout,
            LaunchesPage launchesPage,
            SessionPages sessionPages,
            BookingPages bookingPages,
            BookingRetrievalPages bookingRetrievalPages) {
        this.originCheck = originCheck;
        this.layout = layout;
        this.launchesPage = launchesPage;
        this.sessionPages = sessionPages;
        this.bookingPages = bookingPages;
        this.bookingRetrievalPages = bookingRetrievalPages;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            write(exchange, answer(exchange));
        } finally {
            exchange.close();
        }
    }

    /** The JDK server would drop the connection if a failure left this method. */
    private WebResponse answer(HttpExchange exchange) {
        try {
            return route(exchange);
        } catch (RuntimeException failure) {
            return layout.page(
                    SERVER_ERROR_STATUS,
                    "Something went wrong",
                    HtmlFields.error("The request could not be handled."));
        }
    }

    private WebResponse route(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        if (POST.equals(method) && !originCheck.accepts(exchange.getRequestHeaders().get(ORIGIN_HEADER))) {
            return forbidden();
        }
        String rawPath = exchange.getRequestURI().getRawPath();
        return switch (rawPath) {
            case "/" -> dispatch(method, launchesPage::show, this::methodNotAllowed);
            case "/register" -> dispatch(
                    method, sessionPages::showRegistration, () -> sessionPages.register(readForm(exchange)));
            case "/login" -> dispatch(method, sessionPages::showLogin, () -> sessionPages.login(readForm(exchange)));
            case "/logout" -> dispatch(method, this::methodNotAllowed, sessionPages::logout);
            case "/book" -> dispatch(method, bookingPages::showForm, () -> bookingPages.book(readForm(exchange)));
            case "/bookings" -> dispatch(method, bookingRetrievalPages::showBookings, this::methodNotAllowed);
            case "/style.css" -> dispatch(method, WebRouter::styleSheet, this::methodNotAllowed);
            default -> routeBookingDetail(method, rawPath);
        };
    }

    private WebResponse routeBookingDetail(String method, String rawPath) {
        return BookingIdPath.idFrom(rawPath)
                .map(bookingId -> dispatch(
                        method, () -> bookingRetrievalPages.showBooking(bookingId), this::methodNotAllowed))
                .orElseGet(this::notFound);
    }

    private WebResponse dispatch(String method, Supplier<WebResponse> onGet, Supplier<WebResponse> onPost) {
        return switch (method) {
            case GET -> onGet.get();
            case POST -> onPost.get();
            default -> methodNotAllowed();
        };
    }

    private WebResponse forbidden() {
        return layout.page(
                FORBIDDEN_STATUS, "Request refused", HtmlFields.error("This request came from another website."));
    }

    private WebResponse notFound() {
        return layout.page(NOT_FOUND_STATUS, "Page not found", HtmlFields.error("This page does not exist."));
    }

    private WebResponse methodNotAllowed() {
        return layout.page(
                METHOD_NOT_ALLOWED_STATUS, "Not allowed", HtmlFields.error("This page does not accept that method."));
    }

    private static FormData readForm(HttpExchange exchange) {
        try {
            return FormData.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Comes from the classpath, so that it also works from the jar. */
    private static WebResponse styleSheet() {
        try (InputStream styleSheet = WebRouter.class.getResourceAsStream(STYLE_SHEET)) {
            return WebResponse.css(styleSheet.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(HttpExchange exchange, WebResponse response) throws IOException {
        if (response.isRedirect()) {
            exchange.getResponseHeaders().set("Location", response.location());
            exchange.sendResponseHeaders(response.status(), NO_BODY);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        exchange.sendResponseHeaders(response.status(), response.body().length);
        exchange.getResponseBody().write(response.body());
    }
}
