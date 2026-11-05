package dev.iman.astrobookings.cli;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Asks for a whole number where 0 cancels and an invalid entry asks again (ADR-13). */
final class NumberPrompt {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]+");

    private final Function<String, Optional<String>> promptFor;
    private final PrintStream output;

    NumberPrompt(Function<String, Optional<String>> promptFor, PrintStream output) {
        this.promptFor = promptFor;
        this.output = output;
    }

    /** Asks until the entry is accepted. An empty result means that it was 0 or that the input ended. */
    Optional<BigInteger> promptForNumber(
            String prompt, Predicate<BigInteger> isAccepted, String invalidEntryMessage) {
        Optional<String> entry = promptFor.apply(prompt);
        while (entry.isPresent()) {
            Optional<BigInteger> number = readWholeNumber(entry.get());
            if (number.filter(BigInteger.ZERO::equals).isPresent()) {
                return Optional.empty();
            }
            if (number.filter(isAccepted).isPresent()) {
                return number;
            }
            output.println(invalidEntryMessage);
            entry = promptFor.apply(prompt);
        }
        return Optional.empty();
    }

    /** Only ASCII digits count. {@code BigInteger} takes a number of any length. */
    private static Optional<BigInteger> readWholeNumber(String entry) {
        return WHOLE_NUMBER.matcher(entry).matches() ? Optional.of(new BigInteger(entry)) : Optional.empty();
    }
}
