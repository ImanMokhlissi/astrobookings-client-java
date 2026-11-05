package dev.iman.astrobookings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void should_return_exit_code_0_when_run_without_the_api() {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        PrintStream output = new PrintStream(new ByteArrayOutputStream());

        int exitCode = Main.run(Map.of(), input, output);

        assertEquals(0, exitCode);
    }
}
