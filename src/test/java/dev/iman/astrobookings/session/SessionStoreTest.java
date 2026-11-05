package dev.iman.astrobookings.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.SessionFileFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SessionStoreTest {

    private static final Session SESSION = new Session("token-1", "ana", "ana@example.com");

    @TempDir
    Path folder;

    private Path file;
    private SessionStore store;

    @BeforeEach
    void createStore() {
        file = folder.resolve("session.json");
        store = new SessionStore(file);
    }

    @Test
    void should_return_the_session_when_reading_a_written_file() {
        store.write(SESSION);

        assertEquals(Optional.of(SESSION), store.read());
    }

    @Test
    void should_ignore_unknown_fields_when_reading() throws IOException {
        Files.writeString(file, """
                {"accessToken":"token-1","username":"ana","email":"ana@example.com","theme":"dark"}
                """);

        assertEquals(Optional.of(SESSION), store.read());
    }

    @Test
    void should_create_the_folders_when_writing() {
        Path nestedFile = folder.resolve("a").resolve("b").resolve("session.json");

        new SessionStore(nestedFile).write(SESSION);

        assertTrue(Files.isRegularFile(nestedFile));
    }

    @Test
    void should_store_only_the_token_username_and_email_when_writing() throws IOException {
        store.write(SESSION);

        JsonNode written = new ObjectMapper().readTree(file.toFile());
        Set<String> fieldNames = new HashSet<>();
        written.fieldNames().forEachRemaining(fieldNames::add);
        assertEquals(Set.of("accessToken", "username", "email"), fieldNames);
        assertEquals("token-1", written.get("accessToken").asText());
        assertEquals("ana", written.get("username").asText());
        assertEquals("ana@example.com", written.get("email").asText());
    }

    @Test
    void should_return_no_session_when_the_file_does_not_exist() {
        assertEquals(Optional.empty(), store.read());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not json",
        "",
        "null",
        "[]",
        "{}",
        "{\"username\":\"ana\",\"email\":\"ana@example.com\"}",
        "{\"accessToken\":\"token-1\",\"email\":\"ana@example.com\"}",
        "{\"accessToken\":\"token-1\",\"username\":\"ana\"}",
        "{\"accessToken\":null,\"username\":\"ana\",\"email\":\"ana@example.com\"}",
        "{\"accessToken\":\"  \",\"username\":\"ana\",\"email\":\"ana@example.com\"}",
        "{\"accessToken\":\"token-1\",\"username\":\"  \",\"email\":\"ana@example.com\"}",
        "{\"accessToken\":\"token-1\",\"username\":\"ana\",\"email\":\"\"}"
    })
    void should_fail_when_the_file_cannot_be_read(String content) throws IOException {
        Files.writeString(file, content);

        assertThrows(SessionFileFailure.class, store::read);
    }

    @Test
    void should_fail_when_the_file_cannot_be_read_because_a_directory_is_at_the_path() throws IOException {
        Files.createDirectory(file);

        assertThrows(SessionFileFailure.class, store::read);
    }

    @Test
    void should_fail_when_the_folder_cannot_be_created() throws IOException {
        Path regularFile = Files.writeString(folder.resolve("regular-file"), "text");
        SessionStore storeUnderFile = new SessionStore(regularFile.resolve("session.json"));

        assertThrows(SessionFileFailure.class, () -> storeUnderFile.write(SESSION));
    }

    @Test
    void should_delete_the_file_when_it_exists() {
        store.write(SESSION);

        store.delete();

        assertFalse(Files.exists(file));
    }

    @Test
    void should_not_fail_when_deleting_a_missing_file() {
        store.delete();

        assertFalse(Files.exists(file));
    }

    @Test
    void should_fail_when_the_file_cannot_be_deleted() throws IOException {
        Files.createDirectory(file);
        Files.writeString(file.resolve("inside"), "text");

        assertThrows(SessionFileFailure.class, store::delete);
    }
}
