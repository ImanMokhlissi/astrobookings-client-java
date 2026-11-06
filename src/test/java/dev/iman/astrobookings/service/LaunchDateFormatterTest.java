package dev.iman.astrobookings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LaunchDateFormatterTest {

    @Test
    void should_format_the_date_when_it_has_no_time() {
        assertEquals("December 20, 2026", LaunchDateFormatter.format("2026-12-20"));
    }

    @Test
    void should_format_the_date_in_utc_when_it_is_an_instant() {
        assertEquals("August 25, 2026, 14:00 UTC", LaunchDateFormatter.format("2026-08-25T14:00:00.000Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "next week", "2026-02-30", "12/20/2026", "2026-12-20T14:00:00"})
    void should_keep_the_text_when_the_date_cannot_be_parsed(String text) {
        assertEquals(text, LaunchDateFormatter.format(text));
    }
}
