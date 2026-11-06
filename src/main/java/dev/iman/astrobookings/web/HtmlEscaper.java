package dev.iman.astrobookings.web;

/** Makes text safe in HTML element content and in quoted attributes. */
final class HtmlEscaper {

    private HtmlEscaper() {
    }

    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        text.chars().forEach(character -> escaped.append(escapeCharacter((char) character)));
        return escaped.toString();
    }

    private static String escapeCharacter(char character) {
        return switch (character) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '"' -> "&quot;";
            case '\'' -> "&#39;";
            default -> String.valueOf(character);
        };
    }
}
