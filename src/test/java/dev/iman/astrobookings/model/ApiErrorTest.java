package dev.iman.astrobookings.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorTest {

    @Test
    void should_hold_an_empty_list_when_the_field_errors_are_null() {
        ApiError apiError = new ApiError(400, "Invalid", null);

        assertEquals(List.of(), apiError.fieldErrors());
    }
}
