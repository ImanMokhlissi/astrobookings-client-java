package dev.iman.astrobookings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeatsCheckTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "03", "99999999999999999999"})
    void should_accept_a_whole_number_of_at_least_1_when_reading_seats(String entry) {
        assertEquals(Optional.of(new BigInteger(entry)), SeatsCheck.readSeats(entry));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "00", "1.5", "-1", "+1", "two", "", "   ", "٣"})
    void should_reject_the_entry_when_reading_seats(String entry) {
        assertEquals(Optional.empty(), SeatsCheck.readSeats(entry));
    }
}
