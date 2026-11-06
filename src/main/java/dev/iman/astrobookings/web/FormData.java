package dev.iman.astrobookings.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The fields of a URL-encoded UTF-8 form body. A missing field reads as empty text. */
final class FormData {

    private static final String NO_VALUE = "";

    private final Map<String, String> fields;

    private FormData(Map<String, String> fields) {
        this.fields = Map.copyOf(fields);
    }

    /** The first of repeated fields wins. A malformed encoding raises {@link IllegalArgumentException}. */
    static FormData parse(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : body.split("&")) {
            if (!pair.isEmpty()) {
                addPair(fields, pair);
            }
        }
        return new FormData(fields);
    }

    private static void addPair(Map<String, String> fields, String pair) {
        int separator = pair.indexOf('=');
        String name = separator < 0 ? pair : pair.substring(0, separator);
        String value = separator < 0 ? NO_VALUE : pair.substring(separator + 1);
        fields.putIfAbsent(decode(name), decode(value));
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    String value(String name) {
        return fields.getOrDefault(name, NO_VALUE);
    }
}
