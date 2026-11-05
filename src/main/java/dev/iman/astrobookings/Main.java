package dev.iman.astrobookings;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

/** Entry point of the client, outside the layers. */
public final class Main {

    private static final int EXIT_CODE_SUCCESS = 0;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(System.getenv(), System.in, System.out));
    }

    static int run(Map<String, String> environment, InputStream input, PrintStream output) {
        return EXIT_CODE_SUCCESS;
    }
}
