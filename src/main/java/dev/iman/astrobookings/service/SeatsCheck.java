package dev.iman.astrobookings.service;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Pattern;

/** The check of a typed number, shared by both interfaces. Only ASCII digits count. */
public final class SeatsCheck {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]+");

    private SeatsCheck() {
    }

    /** {@code BigInteger} takes a number of any length. */
    public static Optional<BigInteger> readWholeNumber(String entry) {
        return WHOLE_NUMBER.matcher(entry).matches() ? Optional.of(new BigInteger(entry)) : Optional.empty();
    }

    /** A number of seats is a whole number of at least 1. */
    public static Optional<BigInteger> readSeats(String entry) {
        return readWholeNumber(entry).filter(number -> number.signum() > 0);
    }
}
