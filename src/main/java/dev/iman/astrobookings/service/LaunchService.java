package dev.iman.astrobookings.service;

import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.Launch;
import java.util.List;
import java.util.Set;

/** The use case that lists launches. An API failure passes through unchanged. */
public final class LaunchService {

    private static final Set<String> OPEN_FOR_BOOKING_STATUSES = Set.of("published", "confirmed");

    private final ApiClient apiClient;

    public LaunchService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<Launch> listOpenLaunches() {
        return apiClient.fetchLaunches().stream()
                .filter(this::isOpenForBooking)
                .toList();
    }

    public boolean isOpenForBooking(Launch launch) {
        return OPEN_FOR_BOOKING_STATUSES.contains(launch.status());
    }
}
