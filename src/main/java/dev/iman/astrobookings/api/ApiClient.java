package dev.iman.astrobookings.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iman.astrobookings.model.ActivityResponse;
import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.ApiNotReachableFailure;
import dev.iman.astrobookings.model.AuthResponse;
import dev.iman.astrobookings.model.Booking;
import dev.iman.astrobookings.model.BookingActivityResponse;
import dev.iman.astrobookings.model.BookingResponse;
import dev.iman.astrobookings.model.CreateBookingRequest;
import dev.iman.astrobookings.model.ErrorResponse;
import dev.iman.astrobookings.model.FieldError;
import dev.iman.astrobookings.model.FieldErrorResponse;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.LaunchSummary;
import dev.iman.astrobookings.model.LoginRequest;
import dev.iman.astrobookings.model.RegisterRequest;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import dev.iman.astrobookings.model.User;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** The HTTP boundary: every failure leaves this class as an {@link ApiFailure}. */
public final class ApiClient {

    private static final String ACTIVITIES_PATH = "/activities";
    private static final String USERS_PATH = "/users";
    private static final String LOGIN_PATH = "/login";
    private static final String BOOKINGS_PATH = "/bookings";
    private static final String GENERIC_ERROR_MESSAGE = "The API returned an unexpected response.";
    private static final int NO_RESPONSE_STATUS = 0;
    private static final int UNAUTHORIZED_STATUS = 401;
    private static final int FIRST_SUCCESS_STATUS = 200;
    private static final int LAST_SUCCESS_STATUS = 299;

    private final HttpClient httpClient;
    private final String configuredUrl;
    private final Duration requestTimeout;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
            .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false);

    public ApiClient(HttpClient httpClient, String configuredUrl, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.configuredUrl = configuredUrl;
        this.requestTimeout = requestTimeout;
    }

    public List<Launch> fetchLaunches() {
        return toLaunches(sendExpectingSuccess(buildGetRequest(ACTIVITIES_PATH)));
    }

    public Session register(String username, String email, String password) {
        return authenticate(USERS_PATH, new RegisterRequest(username, email, password, true));
    }

    public Session login(String email, String password) {
        return authenticate(LOGIN_PATH, new LoginRequest(email, password));
    }

    public Booking createBooking(String token, String launchId, BigInteger seats) {
        String json = toJson(new CreateBookingRequest(launchId, seats));
        HttpResponse<String> response =
                sendExpectingSuccess(buildAuthenticatedPostRequest(BOOKINGS_PATH, json, token));
        return toBooking(response, ApiClient::isComplete);
    }

    public List<Booking> fetchBookings(String token) {
        return toRetrievedBookings(sendExpectingSuccess(buildAuthenticatedGetRequest(BOOKINGS_PATH, token)));
    }

    public Booking fetchBooking(String token, String bookingId) {
        HttpResponse<String> response =
                sendExpectingSuccess(buildAuthenticatedGetRequest(bookingPathOf(bookingId), token));
        return toBooking(response, ApiClient::isCompleteWithLaunch);
    }

    /** The id goes as one path segment, whatever characters the API put in it. */
    private static String bookingPathOf(String bookingId) {
        String encodedId = URLEncoder.encode(bookingId, StandardCharsets.UTF_8).replace("+", "%20");
        return BOOKINGS_PATH + "/" + encodedId;
    }

    private Session authenticate(String path, Object requestBody) {
        return toSession(sendExpectingSuccess(buildPostRequest(path, toJson(requestBody))));
    }

    private HttpRequest buildGetRequest(String path) {
        return getRequestBuilder(path).build();
    }

    private HttpRequest buildAuthenticatedGetRequest(String path, String token) {
        return buildWithToken(getRequestBuilder(path), token);
    }

    private HttpRequest.Builder getRequestBuilder(String path) {
        return HttpRequest.newBuilder(urlOf(path)).timeout(requestTimeout).GET();
    }

    private HttpRequest buildPostRequest(String path, String json) {
        return postRequestBuilder(path, json).build();
    }

    private HttpRequest buildAuthenticatedPostRequest(String path, String json, String token) {
        return buildWithToken(postRequestBuilder(path, json), token);
    }

    /** A token that a header cannot hold, such as one with a control character, is a generic error. */
    private static HttpRequest buildWithToken(HttpRequest.Builder builder, String token) {
        try {
            return builder.header("Authorization", "Bearer " + token).build();
        } catch (IllegalArgumentException e) {
            throw new ApiErrorFailure(genericError(NO_RESPONSE_STATUS));
        }
    }

    private HttpRequest.Builder postRequestBuilder(String path, String json) {
        return HttpRequest.newBuilder(urlOf(path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private String toJson(Object requestBody) {
        try {
            return mapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new ApiErrorFailure(genericError(NO_RESPONSE_STATUS));
        }
    }

    private URI urlOf(String path) {
        String base = configuredUrl.endsWith("/")
                ? configuredUrl.substring(0, configuredUrl.length() - 1)
                : configuredUrl;
        return URI.create(base + path);
    }

    private HttpResponse<String> sendExpectingSuccess(HttpRequest request) {
        HttpResponse<String> response = send(request);
        throwIfFailed(response);
        return response;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiNotReachableFailure(configuredUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiNotReachableFailure(configuredUrl, e);
        }
    }

    private void throwIfFailed(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == UNAUTHORIZED_STATUS) {
            throw new UnauthorizedFailure(toApiError(status, response.body()));
        }
        if (!isSuccessful(status)) {
            throw new ApiErrorFailure(toApiError(status, response.body()));
        }
    }

    private static boolean isSuccessful(int status) {
        return status >= FIRST_SUCCESS_STATUS && status <= LAST_SUCCESS_STATUS;
    }

    private List<Launch> toLaunches(HttpResponse<String> response) {
        List<ActivityResponse> activities = readComplete(
                response,
                new TypeReference<List<ActivityResponse>>() {},
                list -> list.stream().allMatch(ApiClient::isComplete));
        return activities.stream().map(ApiClient::toLaunch).toList();
    }

    private static boolean isComplete(ActivityResponse activity) {
        return activity != null && noneIsNull(
                activity.id(),
                activity.name(),
                activity.slug(),
                activity.price(),
                activity.date(),
                activity.duration(),
                activity.location(),
                activity.minParticipants(),
                activity.maxParticipants(),
                activity.status());
    }

    private static Launch toLaunch(ActivityResponse activity) {
        return new Launch(
                activity.id(),
                activity.name(),
                activity.slug(),
                activity.price(),
                activity.date(),
                activity.duration(),
                activity.location(),
                activity.minParticipants(),
                activity.maxParticipants(),
                activity.status());
    }

    private Session toSession(HttpResponse<String> response) {
        return toSession(readComplete(response, new TypeReference<AuthResponse>() {}, ApiClient::isComplete));
    }

    private static boolean isComplete(AuthResponse authResponse) {
        return authResponse.user() != null
                && hasText(authResponse.accessToken())
                && hasText(authResponse.user().username())
                && hasText(authResponse.user().email());
    }

    private static Session toSession(AuthResponse authResponse) {
        User user = authResponse.user();
        return new Session(authResponse.accessToken(), user.username(), user.email());
    }

    private Booking toBooking(HttpResponse<String> response, Predicate<BookingResponse> isComplete) {
        return toBooking(readComplete(response, new TypeReference<BookingResponse>() {}, isComplete));
    }

    private static boolean isComplete(BookingResponse booking) {
        return hasText(booking.id())
                && hasText(booking.activityId())
                && hasText(booking.userId())
                && booking.participants() != null
                && hasText(booking.createdAt())
                && hasText(booking.paymentStatus())
                && (booking.activity() == null || isComplete(booking.activity()));
    }

    private List<Booking> toRetrievedBookings(HttpResponse<String> response) {
        List<BookingResponse> bookings = readComplete(
                response,
                new TypeReference<List<BookingResponse>>() {},
                list -> list.stream().allMatch(ApiClient::isCompleteWithLaunch));
        return bookings.stream().map(ApiClient::toBooking).toList();
    }

    /** Only a newly created booking may lack its launch, so a retrieved one must hold it. */
    private static boolean isCompleteWithLaunch(BookingResponse booking) {
        return booking != null && booking.activity() != null && isComplete(booking);
    }

    private static boolean isComplete(BookingActivityResponse activity) {
        return noneIsNull(
                activity.name(),
                activity.slug(),
                activity.price(),
                activity.date(),
                activity.duration(),
                activity.location(),
                activity.status());
    }

    private static Booking toBooking(BookingResponse booking) {
        return new Booking(
                booking.id(),
                booking.activityId(),
                booking.userId(),
                booking.participants(),
                booking.createdAt(),
                booking.paymentId(),
                booking.paymentStatus(),
                booking.activity() == null ? null : toLaunchSummary(booking.activity()));
    }

    private static LaunchSummary toLaunchSummary(BookingActivityResponse activity) {
        return new LaunchSummary(
                activity.name(),
                activity.slug(),
                activity.price(),
                activity.date(),
                activity.duration(),
                activity.location(),
                activity.status());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean noneIsNull(Object... values) {
        return Stream.of(values).noneMatch(Objects::isNull);
    }

    /** A 2xx body that cannot be read or is not complete becomes a generic error. */
    private <T> T readComplete(
            HttpResponse<String> response, TypeReference<T> bodyType, Predicate<T> isComplete) {
        return readJson(response.body(), bodyType)
                .filter(isComplete)
                .orElseThrow(() -> new ApiErrorFailure(genericError(response.statusCode())));
    }

    /** An empty result means that the body is not valid JSON of this type, or is the literal null. */
    private <T> Optional<T> readJson(String body, TypeReference<T> bodyType) {
        try {
            return Optional.ofNullable(mapper.readValue(body, bodyType));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private ApiError toApiError(int status, String body) {
        return readJson(body, new TypeReference<ErrorResponse>() {})
                .filter(ApiClient::isWellFormed)
                .map(error -> new ApiError(status, error.message(), toFieldErrors(error.errors())))
                .orElseGet(() -> genericError(status));
    }

    private static boolean isWellFormed(ErrorResponse error) {
        return hasText(error.message())
                && (error.errors() == null || error.errors().stream().allMatch(ApiClient::isWellFormed));
    }

    private static boolean isWellFormed(FieldErrorResponse fieldError) {
        return fieldError != null && fieldError.field() != null && fieldError.message() != null;
    }

    private static List<FieldError> toFieldErrors(List<FieldErrorResponse> errors) {
        if (errors == null) {
            return List.of();
        }
        return errors.stream().map(error -> new FieldError(error.field(), error.message())).toList();
    }

    private static ApiError genericError(int status) {
        return new ApiError(status, GENERIC_ERROR_MESSAGE, List.of());
    }
}
