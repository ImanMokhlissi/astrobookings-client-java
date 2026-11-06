package dev.iman.astrobookings.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HtmlEscaperTest {

    @Test
    void should_escape_each_html_character_when_escaping() {
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&#39;f", HtmlEscaper.escape("a&b<c>d\"e'f"));
    }
}
