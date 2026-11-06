package dev.iman.astrobookings.cli;

import dev.iman.astrobookings.service.SeatsCheck;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Asks for a whole number where 0 cancels and an invalid entry asks again (ADR-13). */
final class NumberPrompt {

    private final Function<String, Optional<String>> promptFor;
    private final PrintStream output;

    NumberPrompt(Function<String, Optional<String>> promptFor, PrintStream output) {
        this.promptFor = promptFor;
        this.output = output;
    }

    /** Shows one line for each item, numbered from 1, so that each number points at its item. */
    <T> void showNumbered(List<T> items, Function<T, String> describe) {
        for (int index = 0; index < items.size(); index++) {
            output.println((index + 1) + ". " + describe.apply(items.get(index)));
        }
    }

    /** Resolves the entry to the item in a list shown with {@link #showNumbered}. */
    <T> Optional<T> chooseFromList(String prompt, List<T> items, String invalidEntryMessage) {
        BigInteger lastNumber = BigInteger.valueOf(items.size());
        return promptForNumber(prompt, number -> number.compareTo(lastNumber) <= 0, invalidEntryMessage)
                .map(number -> items.get(number.intValueExact() - 1));
    }

    /** Asks until the entry is accepted. An empty result means that it was 0 or that the input ended. */
    Optional<BigInteger> promptForNumber(
            String prompt, Predicate<BigInteger> isAccepted, String invalidEntryMessage) {
        Optional<String> entry = promptFor.apply(prompt);
        while (entry.isPresent()) {
            Optional<BigInteger> number = SeatsCheck.readWholeNumber(entry.get());
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
}
