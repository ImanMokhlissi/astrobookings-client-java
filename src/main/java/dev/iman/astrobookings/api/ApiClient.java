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
import dev.iman.astrobookings.model.ErrorResponse;
import dev.iman.astrobookings.model.FieldError;
import dev.iman.astrobookings.model.FieldErrorResponse;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** The HTTP boundary: every failure leaves this class as an {@link ApiFailure}. */
public final class ApiClient {

    private static final String ACTIVITIES_PATH = "/activities";
    private static final String GENERIC_ERROR_MESSAGE = "The API returned an unexpected response.";
    private static final int UNAUTHORIZED_STATUS = 401;

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
        HttpResponse<String> response = send(buildGetRequest(ACTIVITIES_PATH));
        throwIfFailed(response);
        return toLaunches(response);
    }

    private HttpRequest buildGetRequest(String path) {
        return HttpRequest.newBuilder(urlOf(path)).timeout(requestTimeout).GET().build();
    }

    private URI urlOf(String path) {
        String base = configuredUrl.endsWith("/")
                ? configuredUrl.substring(0, configuredUrl.length() - 1)
                : configuredUrl;
        return URI.create(base + path);
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
        if (status < 200 || status >= 300) {
            throw new ApiErrorFailure(toApiError(status, response.body()));
        }
    }

    private List<Launch> toLaunches(HttpResponse<String> response) {
        try {
            List<ActivityResponse> activities =
                    mapper.readValue(response.body(), new TypeReference<List<ActivityResponse>>() {});
            if (activities == null || activities.stream().anyMatch(ApiClient::isIncomplete)) {
                throw new ApiErrorFailure(genericError(response.statusCode()));
            }
            return activities.stream().map(ApiClient::toLaunch).toList();
        } catch (JsonProcessingException e) {
            throw new ApiErrorFailure(genericError(response.statusCode()));
        }
    }

    private static boolean isIncomplete(ActivityResponse activity) {
        return activity == null || Stream.<Object>of(
                activity.id(),
                activity.name(),
                activity.slug(),
                activity.price(),
                activity.date(),
                activity.duration(),
                activity.location(),
                activity.minParticipants(),
                activity.maxParticipants(),
                activity.status())
                .anyMatch(Objects::isNull);
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

    private ApiError toApiError(int status, String body) {
        try {
            ErrorResponse error = mapper.readValue(body, ErrorResponse.class);
            if (!isWellFormed(error)) {
                return genericError(status);
            }
            return new ApiError(status, error.message(), toFieldErrors(error.errors()));
        } catch (JsonProcessingException e) {
            return genericError(status);
        }
    }

    private static boolean isWellFormed(ErrorResponse error) {
        return error != null
                && error.message() != null
                && !error.message().isBlank()
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
