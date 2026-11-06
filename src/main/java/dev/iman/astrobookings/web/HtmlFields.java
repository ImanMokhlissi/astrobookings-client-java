package dev.iman.astrobookings.web;

/** The pieces of HTML that pages share, with the same wording as the console. */
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

    /** A form that posts to a path of this server, with its controls above the submit button. */
    static String form(String action, String submitLabel, String... controls) {
        return "<form method=\"post\" action=\"" + action + "\">\n"
                + String.join("", controls)
                + "<button type=\"submit\">" + submitLabel + "</button>\n"
                + "</form>\n";
    }

    static String textInput(String name, String label, String typedValue) {
        return "<label for=\"" + name + "\">" + label + "</label>\n"
                + "<input type=\"text\" id=\"" + name + "\" name=\"" + name + "\""
                + " value=\"" + HtmlEscaper.escape(typedValue) + "\">\n";
    }

    /** It takes no value, so that no form can echo a password. */
    static String passwordInput() {
        return "<label for=\"password\">Password</label>\n"
                + "<input type=\"password\" id=\"password\" name=\"password\">\n";
    }
}
