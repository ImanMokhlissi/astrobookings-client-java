package dev.iman.astrobookings.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingTest {

    @Test
    void should_accept_no_launch_and_no_payment_id_when_built_for_a_created_booking() {
        Booking booking = new Booking("booking-1", "launch-1", "user-1", 2, "2026-11-05", null, "paid", null);

        assertNull(booking.launch());
        assertNull(booking.paymentId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "launchId", "userId", "createdAt", "paymentStatus"})
    void should_fail_when_a_required_field_is_null(String field) {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new Booking(
                        valueUnless(field, "id"),
                        valueUnless(field, "launchId"),
                        valueUnless(field, "userId"),
                        2,
                        valueUnless(field, "createdAt"),
                        "payment-1",
                        valueUnless(field, "paymentStatus"),
                        null));

        assertEquals(field, failure.getMessage());
    }

    private static String valueUnless(String nullField, String field) {
        return nullField.equals(field) ? null : "text";
    }
}
