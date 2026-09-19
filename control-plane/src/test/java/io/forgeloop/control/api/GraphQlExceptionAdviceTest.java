package io.forgeloop.control.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GraphQlExceptionAdviceTest {
    @Test
    void returnsSafeBadRequestForInvalidInput() {
        var error = new GraphQlExceptionAdvice().invalidInput(new IllegalArgumentException("Runner credentials are invalid"));

        assertEquals("Runner credentials are invalid", error.getMessage());
        assertEquals("BAD_REQUEST", error.getErrorType().toString());
    }
}
