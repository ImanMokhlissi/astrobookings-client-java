package example;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A use case: receives its collaborators through the constructor and holds the client's rules. */
public final class ExampleService {

    private static final Set<String> OPEN_STATUSES = Set.of("open");

    private final ExampleApiClient apiClient;

    public ExampleService(ExampleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<ExampleRecord> listOpenItems() {
        return apiClient.fetchItems().stream()
                .filter(item -> OPEN_STATUSES.contains(item.status()))
                .toList();
    }

    public Optional<ExampleRecord> findOpenItemByName(String name) {
        return listOpenItems().stream()
                .filter(item -> item.name().equals(name))
                .findFirst();
    }
}
