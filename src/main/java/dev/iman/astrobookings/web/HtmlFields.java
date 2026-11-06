package dev.iman.astrobookings.web;

/** The "label: value" lines that pages show, with the same wording as the console. */
final class HtmlFields {

    private HtmlFields() {
    }

    static String field(String label, String value) {
        return "<li>" + label + ": " + HtmlEscaper.escape(value) + "</li>\n";
    }

    static String list(String... fields) {
        return "<ul class=\"fields\">\n" + String.join("", fields) + "</ul>\n";
    }

    /** A message from the client's own texts, with no markup to escape. */
    static String message(String text) {
        return "<p>" + text + "</p>\n";
    }

    static String error(String text) {
        return "<p class=\"error\">" + text + "</p>\n";
    }
}
