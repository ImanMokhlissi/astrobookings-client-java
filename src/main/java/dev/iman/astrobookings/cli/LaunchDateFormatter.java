package dev.iman.astrobookings.cli;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** Formats a launch's date for display, independently of the machine's language and time zone. */
final class LaunchDateFormatter {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private LaunchDateFormatter() {
    }

    /** Returns the date for display, or the same text when it is neither a date nor an instant. */
    static String format(String text) {
        return formatAsDate(text).or(() -> formatAsInstant(text)).orElse(text);
    }

    private static Optional<String> formatAsDate(String text) {
        try {
            return Optional.of(DATE_FORMAT.format(LocalDate.parse(text)));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> formatAsInstant(String text) {
        try {
            return Optional.of(INSTANT_FORMAT.format(Instant.parse(text)));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
