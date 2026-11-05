package dev.iman.astrobookings.cli;

import java.util.Optional;

/** Reads a password. {@code java.io.Console} is final, so a test stands in for it through this. */
@FunctionalInterface
public interface PasswordPrompt {

    /** Shows the prompt and returns the password, or an empty result at end of input. */
    Optional<String> readPassword(String prompt);
}
