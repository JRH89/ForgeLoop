package io.forgeloop.control.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.forgeloop.control.application.ArtifactDownloadService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ArtifactDownloadControllerTest {
    @Test void runnerSuppliedArtifactsAreServedInsideASandbox() {
        ArtifactDownloadService service = mock(ArtifactDownloadService.class);
        when(service.download("artifact-1")).thenReturn(new ArtifactDownloadService.Download(
                "application/json", "evidence.json", "{}".getBytes(StandardCharsets.UTF_8)));

        var response = new ArtifactDownloadController(service).download("artifact-1");

        assertEquals("sandbox; default-src 'none'", response.getHeaders().getFirst("Content-Security-Policy"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("inline; filename=\"evidence.json\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }
}
