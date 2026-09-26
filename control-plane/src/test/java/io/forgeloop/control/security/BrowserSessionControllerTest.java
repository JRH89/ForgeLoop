package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class BrowserSessionControllerTest {
    private final OperatorContext operators = mock(OperatorContext.class);

    @Test
    void permitsTheExplicitDevelopmentModeWithoutAnAuthenticationPrincipal() {
        BrowserSessionController controller = new BrowserSessionController(operators, "development");

        assertEquals(204, controller.session(null).getStatusCode().value());
    }

    @Test
    void rejectsAnAnonymousSessionInAuthenticatedModes() {
        BrowserSessionController controller = new BrowserSessionController(operators, "github");

        assertEquals(401, controller.session(null).getStatusCode().value());
    }
}
