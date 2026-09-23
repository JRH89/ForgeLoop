package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestRateLimitFilterTest {
    @Test void rejectsRequestsBeyondTheConfiguredWindow() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(2, Clock.fixed(Instant.parse("2026-09-23T12:00:10Z"), ZoneOffset.UTC));
        AtomicInteger accepted = new AtomicInteger();

        assertEquals(200, invoke(filter, accepted).getStatus());
        assertEquals(200, invoke(filter, accepted).getStatus());
        MockHttpServletResponse rejected = invoke(filter, accepted);

        assertEquals(429, rejected.getStatus());
        assertEquals("50", rejected.getHeader("Retry-After"));
        assertEquals(2, accepted.get());
    }

    @Test void healthChecksAreNeverRateLimited() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(1, Clock.systemUTC());
        AtomicInteger accepted = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/readiness");

        for (int count = 0; count < 3; count++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> accepted.incrementAndGet());
        }
        assertEquals(3, accepted.get());
    }

    private static MockHttpServletResponse invoke(RequestRateLimitFilter filter, AtomicInteger accepted) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> accepted.incrementAndGet());
        return response;
    }
}
