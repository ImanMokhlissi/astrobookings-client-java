package dev.iman.astrobookings.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiNotReachableFailure;
import dev.iman.astrobookings.model.FieldError;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApiClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);
    private static final String GENERIC_MESSAGE = "The API returned an unexpected response.";

    private FakeApiServer fakeApi;

    @BeforeEach
    void startServer() throws IOException {
        fakeApi = FakeApiServer.start();
    }

    @AfterEach
    void stopServer() {
        fakeApi.stop();
    }

    @Test
    void should_return_every_launch_with_its_fields_when_the_api_responds_ok() {
        fakeApi.respondWith(200, """
                [{"id":"activity-1","name":"Moon","slug":"moon","price":158.50,"date":"2026-12-20",
                  "duration":90,"location":"Kourou","minParticipants":3,"maxParticipants":15,"status":"draft"},
                 {"id":"activity-2","name":"Mars","slug":"mars","price":35,"date":"2026-08-25T14:00:00.000Z",
                  "duration":120,"location":"Cape","minParticipants":1,"maxParticipants":2,"status":"published"}]
                """);

        List<Launch> launches = apiClient(fakeApi.url()).fetchLaunches();

        assertEquals(
                List.of(
                        new Launch("activity-1", "Moon", "moon", new BigDecimal("158.50"), "2026-12-20", 90, "Kourou", 3, 15, "draft"),
                        new Launch("activity-2", "Mars", "mars", new BigDecimal("35"), "2026-08-25T14:00:00.000Z", 120, "Cape", 1, 2, "published")),
                launches);
    }

    @Test
    void should_send_a_public_get_to_the_activities_path_when_fetching() {
        apiClient(fakeApi.url()).fetchLaunches();

        assertEquals("GET", fakeApi.receivedMethod());
        assertEquals("/activities", fakeApi.receivedPath());
        assertNull(fakeApi.receivedAuthorization());
    }

    @Test
    void should_return_no_launches_when_the_api_responds_with_an_empty_list() {
        fakeApi.respondWith(200, "[]");

        assertEquals(List.of(), apiClient(fakeApi.url()).fetchLaunches());
    }

    @Test
    void should_ignore_the_field_when_an_activity_holds_an_unknown_one() {
        String activity = FakeApiServer.activityJson("Moon", "published");
        fakeApi.respondWith(200, "[" + activity.replace("{", "{\"rocket\":{\"id\":7},\"createdAt\":\"now\",") + "]");

        List<Launch> launches = apiClient(fakeApi.url()).fetchLaunches();

        assertEquals("Moon", launches.get(0).name());
    }

    @ParameterizedTest
    @CsvSource({"/, /activities", "/v1, /v1/activities", "/v1/, /v1/activities"})
    void should_call_the_configured_url_when_it_has_a_path_or_a_trailing_slash(String suffix, String expectedPath) {
        apiClient(fakeApi.url() + suffix).fetchLaunches();

        assertEquals(expectedPath, fakeApi.receivedPath());
    }

    @Test
    void should_fail_as_not_reachable_when_nothing_listens_on_the_port() {
        fakeApi.stop();

        ApiNotReachableFailure failure =
                assertThrows(ApiNotReachableFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());

        assertEquals(fakeApi.url(), failure.url());
    }

    @Test
    void should_fail_as_not_reachable_when_the_call_times_out() {
        fakeApi.blockResponses();
        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), SHORT_TIMEOUT);

        ApiNotReachableFailure failure = assertThrows(ApiNotReachableFailure.class, apiClient::fetchLaunches);

        assertEquals(fakeApi.url(), failure.url());
    }

    @Test
    void should_keep_the_configured_text_when_the_url_has_a_trailing_slash_and_cannot_be_reached() {
        fakeApi.stop();
        String configuredUrl = fakeApi.url() + "/";

        ApiNotReachableFailure failure =
                assertThrows(ApiNotReachableFailure.class, () -> apiClient(configuredUrl).fetchLaunches());

        assertEquals(configuredUrl, failure.url());
    }

    @Test
    void should_restore_the_interrupt_flag_when_the_call_is_interrupted() {
        ApiClient apiClient = apiClient(fakeApi.url());
        Thread.currentThread().interrupt();

        assertThrows(ApiNotReachableFailure.class, apiClient::fetchLaunches);

        assertTrue(Thread.interrupted());
    }

    @Test
    void should_fail_as_unauthorized_when_the_api_responds_401() {
        fakeApi.respondWith(401, "{\"message\":\"Unauthorized\",\"errors\":[]}");

        UnauthorizedFailure failure =
                assertThrows(UnauthorizedFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());

        assertEquals(new ApiError(401, "Unauthorized", List.of()), failure.apiError());
    }

    @Test
    void should_fail_as_unauthorized_with_a_generic_error_when_the_401_body_is_malformed() {
        fakeApi.respondWith(401, "<html>");

        UnauthorizedFailure failure =
                assertThrows(UnauthorizedFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());

        assertEquals(new ApiError(401, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_the_message_and_field_errors_when_the_api_responds_with_an_error() {
        fakeApi.respondWith(400, """
                {"message":"Invalid data","errors":[{"field":"email","message":"Already taken"}]}
                """);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(
                new ApiError(400, "Invalid data", List.of(new FieldError("email", "Already taken"))),
                failure.apiError());
    }

    @Test
    void should_hold_no_field_errors_when_the_error_has_no_errors() {
        fakeApi.respondWith(404, "{\"message\":\"Activity not found\"}");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(404, "Activity not found", List.of()), failure.apiError());
    }

    @Test
    void should_ignore_the_field_when_an_error_holds_an_unknown_one() {
        fakeApi.respondWith(500, """
                {"message":"Broken","code":7,"errors":[{"field":"name","message":"Bad","hint":"x"}]}
                """);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(500, "Broken", List.of(new FieldError("name", "Bad"))), failure.apiError());
    }

    @Test
    void should_fail_with_an_api_error_when_the_status_is_a_redirect() {
        fakeApi.respondWith(302, "");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(302, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_a_generic_error_when_the_body_is_not_valid_json() {
        fakeApi.respondWith(200, "<html>not json</html>");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_a_generic_error_when_the_body_is_json_null() {
        fakeApi.respondWith(200, "null");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @Test
    void should_fail_with_a_generic_error_when_an_activity_misses_a_field() {
        fakeApi.respondWith(200, "[{\"id\":\"activity-1\",\"name\":\"Moon\"}]");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name", "slug", "price", "date", "duration", "location", "minParticipants", "maxParticipants", "status"})
    void should_fail_with_a_generic_error_when_any_field_of_an_activity_is_missing(String field) {
        String activity = FakeApiServer.activityJson("Moon", "published");
        fakeApi.respondWith(200, "[" + activity.replace("\"" + field + "\":", "\"other\":") + "]");

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(GENERIC_MESSAGE, failure.apiError().message());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "{}",
        "{\"id\":\"activity-1\"}",
        "\"text\"",
        "7",
        "[null]",
        "[7]",
        "[[]]",
        "[] trailing",
        "[{\"id\":\"a\",\"name\":\"n\",\"slug\":\"s\",\"price\":1,\"date\":\"d\",\"duration\":1.5,"
                + "\"location\":\"l\",\"minParticipants\":1,\"maxParticipants\":2,\"status\":\"published\"}]"
    })
    void should_fail_with_a_generic_error_when_the_ok_body_is_malformed(String body) {
        fakeApi.respondWith(200, body);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(200, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "<html>",
        "null",
        "[]",
        "{}",
        "{\"errors\":[]}",
        "{\"message\":null}",
        "{\"message\":\"Bad\",\"errors\":\"text\"}",
        "{\"message\":\"Bad\",\"errors\":{}}",
        "{\"message\":\"Bad\",\"errors\":[null]}",
        "{\"message\":\"Bad\",\"errors\":[7]}",
        "{\"message\":\"Bad\",\"errors\":[{\"field\":\"name\"}]}",
        "{\"message\":\"Bad\",\"errors\":[{\"message\":\"Bad\"}]}"
    })
    void should_fail_with_a_generic_error_when_the_error_body_is_malformed(String body) {
        fakeApi.respondWith(400, body);

        ApiErrorFailure failure = fetchExpectingApiError();

        assertEquals(new ApiError(400, GENERIC_MESSAGE, List.of()), failure.apiError());
    }

    private ApiErrorFailure fetchExpectingApiError() {
        return assertThrows(ApiErrorFailure.class, () -> apiClient(fakeApi.url()).fetchLaunches());
    }

    private ApiClient apiClient(String url) {
        return new ApiClient(HttpClient.newHttpClient(), url, TIMEOUT);
    }
}
