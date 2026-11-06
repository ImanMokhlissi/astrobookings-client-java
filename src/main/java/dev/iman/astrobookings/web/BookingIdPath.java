package dev.iman.astrobookings.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/** The path of a booking's detail, with the id as exactly one percent-encoded segment. */
final class BookingIdPath {

    private static final String PREFIX = "/bookings/";
    private static final Set<String> UNUSABLE_IDS = Set.of("", ".", "..");
    private static final char REPLACEMENT_CHARACTER = '�';

    private BookingIdPath() {
    }

    static String pathOf(String bookingId) {
        return PREFIX + URLEncoder.encode(bookingId, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Reads the raw path, because a decoded one would merge an encoded slash into the path. */
    static Optional<String> idFrom(String rawPath) {
        if (!rawPath.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String segment = rawPath.substring(PREFIX.length());
        return segment.contains("/") ? Optional.empty() : decode(segment).filter(BookingIdPath::isUsable);
    }

    /** A {@code +} in a path is a plus sign, not a space as in a form. */
    private static Optional<String> decode(String segment) {
        try {
            return Optional.of(URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The replacement character is what the decoder leaves for bytes that are not valid UTF-8. */
    private static boolean isUsable(String bookingId) {
        return !UNUSABLE_IDS.contains(bookingId) && bookingId.indexOf(REPLACEMENT_CHARACTER) < 0;
    }
}
