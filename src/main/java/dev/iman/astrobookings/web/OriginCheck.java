package dev.iman.astrobookings.web;

import java.util.List;
import java.util.Set;

/**
 * Tells a POST from this server's own pages from one sent by another website (ADR-17). The origins
 * are a fixed list with the bound port and never come from {@code Host}, which an attacker's domain
 * could control.
 */
final class OriginCheck {

    private final Set<String> ownOrigins;

    OriginCheck(int boundPort) {
        ownOrigins = Set.of("http://127.0.0.1:" + boundPort, "http://localhost:" + boundPort);
    }

    /** The values of the header, or null when the request has none, which is accepted. */
    boolean accepts(List<String> originValues) {
        return originValues == null || ownOrigins.containsAll(originValues);
    }
}
