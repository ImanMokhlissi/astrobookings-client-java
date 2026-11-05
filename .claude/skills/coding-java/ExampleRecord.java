package example;

import java.util.List;
import java.util.Objects;

/** Plain data as a record: required fields are checked, and the collection is never null. */
public record ExampleRecord(String id, String name, String status, List<String> tags) {

    public ExampleRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
