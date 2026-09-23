package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ArtifactUploadSizeFilterTest {
    @Test void rejectsOversizedBodiesBeforeControllerAllocation() throws Exception {
        ArtifactUploadSizeFilter filter = new ArtifactUploadSizeFilter(4);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/runner/artifacts");
        request.setContent(new byte[5]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger accepted = new AtomicInteger();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> accepted.incrementAndGet());

        assertEquals(413, response.getStatus());
        assertEquals(0, accepted.get());
    }

    @Test void requiresLengthForArtifactUploads() throws Exception {
        ArtifactUploadSizeFilter filter = new ArtifactUploadSizeFilter(4);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/runner/artifacts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertEquals(411, response.getStatus());
    }
}
