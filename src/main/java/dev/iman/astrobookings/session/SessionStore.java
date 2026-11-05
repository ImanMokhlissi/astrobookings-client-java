package dev.iman.astrobookings.session;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.SessionFileFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/** The session file boundary: every failure leaves this class as a {@link SessionFileFailure}. */
public final class SessionStore {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    public SessionStore(Path file) {
        this.file = file;
    }

    /**
     * An empty result means that there is no file. {@link Session} rejects a missing, null or blank
     * field, and Jackson reports that as a processing failure.
     */
    public Optional<Session> read() {
        try {
            return Optional.of(parse(Files.readString(file)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new SessionFileFailure("The session file could not be read: " + file, e);
        }
    }

    private Session parse(String content) throws IOException {
        Session session = mapper.readValue(content, Session.class);
        if (session == null) {
            throw new SessionFileFailure("The session file holds no session: " + file);
        }
        return session;
    }

    public void write(Session session) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, mapper.writeValueAsString(session));
        } catch (IOException e) {
            throw new SessionFileFailure("The session file could not be written: " + file, e);
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new SessionFileFailure("The session file could not be deleted: " + file, e);
        }
    }
}
