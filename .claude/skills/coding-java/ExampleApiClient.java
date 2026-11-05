package example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** The HTTP boundary: every failure leaves this class as one of the client's own exceptions. */
public final class ExampleApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String GENERIC_ERROR = "The API returned an unexpected response";

    private final HttpClient httpClient;
    private final URI baseUrl;
    private final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ExampleApiClient(HttpClient httpClient, URI baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    public List<ExampleRecord> fetchItems() {
        HttpResponse<String> response = send(requestTo("/items").GET().build());
        List<ExampleRecord> items = parse(response, new TypeReference<>() {});
        if (items.contains(null)) {
            throw genericError(response.statusCode());
        }
        return items;
    }

    /** Appends the path as text, because URI.resolve would drop a path in the configured URL. */
    private HttpRequest.Builder requestTo(String path) {
        String base = baseUrl.toString().replaceAll("/+$", "");
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(TIMEOUT);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            throwIfFailed(response);
            return response;
        } catch (IOException e) {
            throw new UnreachableException(baseUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnreachableException(baseUrl);
        }
    }

    private void throwIfFailed(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 401) {
            throw new UnauthorizedException();
        }
        if (status < 200 || status >= 300) {
            throw toApiError(status, response.body());
        }
    }

    private ApiErrorException toApiError(int status, String body) {
        try {
            ErrorResponse error = mapper.readValue(body, ErrorResponse.class);
            if (!isWellFormed(error)) {
                return genericError(status);
            }
            return new ApiErrorException(status, error.message(), toFieldErrors(error.errors()));
        } catch (JsonProcessingException e) {
            return genericError(status);
        }
    }

    /** A valid JSON body can still be null, or hold null entries, so readValue is not enough. */
    private boolean isWellFormed(ErrorResponse error) {
        if (error == null || error.message() == null || error.message().isBlank()) {
            return false;
        }
        return error.errors() == null || error.errors().stream().allMatch(this::isWellFormed);
    }

    private boolean isWellFormed(FieldErrorResponse error) {
        return error != null && error.field() != null && error.message() != null;
    }

    private List<FieldError> toFieldErrors(List<FieldErrorResponse> errors) {
        if (errors == null) {
            return List.of();
        }
        return errors.stream().map(e -> new FieldError(e.field(), e.message())).toList();
    }

    private <T> T parse(HttpResponse<String> response, TypeReference<T> type) {
        try {
            T value = mapper.readValue(response.body(), type);
            if (value == null) {
                throw genericError(response.statusCode());
            }
            return value;
        } catch (JsonProcessingException e) {
            throw genericError(response.statusCode());
        }
    }

    private ApiErrorException genericError(int status) {
        return new ApiErrorException(status, GENERIC_ERROR, List.of());
    }

    /** The API's error format, read only inside this class. */
    record ErrorResponse(String message, List<FieldErrorResponse> errors) {}

    record FieldErrorResponse(String field, String message) {}

    public record FieldError(String field, String message) {}

    /** The base of the client's own exceptions. The compiler knows every failure type. */
    public abstract static sealed class ClientException extends RuntimeException
            permits UnreachableException, UnauthorizedException, ApiErrorException {

        ClientException(String message) {
            super(message);
        }
    }

    public static final class UnreachableException extends ClientException {

        UnreachableException(URI baseUrl) {
            super("API not reachable at " + baseUrl);
        }
    }

    public static final class UnauthorizedException extends ClientException {

        UnauthorizedException() {
            super("The API rejected the request as unauthorized");
        }
    }

    public static final class ApiErrorException extends ClientException {

        private final int status;
        private final List<FieldError> fieldErrors;

        ApiErrorException(int status, String message, List<FieldError> fieldErrors) {
            super(message);
            this.status = status;
            this.fieldErrors = List.copyOf(fieldErrors);
        }

        public int status() {
            return status;
        }

        public List<FieldError> fieldErrors() {
            return fieldErrors;
        }
    }
}
