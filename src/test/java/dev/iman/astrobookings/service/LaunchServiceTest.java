package dev.iman.astrobookings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.iman.astrobookings.FakeApiServer;
import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.Launch;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaunchServiceTest {

    private FakeApiServer fakeApi;
    private LaunchService launchService;

    @BeforeEach
    void startServer() throws IOException {
        fakeApi = FakeApiServer.start();
        launchService = new LaunchService(
                new ApiClient(HttpClient.newHttpClient(), fakeApi.url(), Duration.ofSeconds(10)));
    }

    @AfterEach
    void stopServer() {
        fakeApi.stop();
    }

    @Test
    void should_keep_published_and_confirmed_launches_when_listing() {
        respondWithStatuses("published", "confirmed");

        List<Launch> launches = launchService.listOpenLaunches();

        assertEquals(List.of("published", "confirmed"), launches.stream().map(Launch::status).toList());
    }

    @Test
    void should_leave_out_every_other_status_when_listing() {
        respondWithStatuses("draft", "sold-out", "published", "done", "cancelled", "Published", "unknown");

        List<Launch> launches = launchService.listOpenLaunches();

        assertEquals(List.of("published"), launches.stream().map(Launch::status).toList());
    }

    @Test
    void should_return_no_launches_when_none_is_open_for_booking() {
        respondWithStatuses("draft", "done");

        assertEquals(List.of(), launchService.listOpenLaunches());
    }

    private void respondWithStatuses(String... statuses) {
        String activities = Stream.of(statuses)
                .map(status -> FakeApiServer.activityJson("Launch-" + status, status))
                .collect(Collectors.joining(",", "[", "]"));
        fakeApi.respondWith(200, activities);
    }
}
