package dev.iman.astrobookings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tagged test that AC6, AC7 and AC8 of chore-build-setup observe in the console output of Maven.
 * It needs no API, because its input exits the menu before any HTTP call.
 */
@Tag("e2e")
class BuildSetupE2eTest {

    private static final String URL_WITH_NOTHING_LISTENING = "http://localhost:1";

    @Test
    void AC8_should_run_main_with_exit_code_0_when_selected_by_the_e2e_profile(@TempDir Path temporaryFolder) {
        ByteArrayInputStream input = new ByteArrayInputStream("0\n".getBytes());
        PrintStream output = new PrintStream(new ByteArrayOutputStream());
        Map<String, String> environment = Map.of("ASTROBOOKINGS_API_URL", URL_WITH_NOTHING_LISTENING);

        int exitCode = Main.run(environment, input, output, temporaryFolder.resolve("session.json"));

        assertEquals(0, exitCode);
    }
}
