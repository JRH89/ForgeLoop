package io.forgeloop.control.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.application.ArtifactUploadService;
import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.domain.ArtifactMetadata;
import org.junit.jupiter.api.Test;

class RunnerArtifactControllerTest {
    @Test void authenticatesRunnerAndForwardsLeaseBoundArtifact() {
        RunnerService runners = mock(RunnerService.class);
        ArtifactUploadService artifacts = mock(ArtifactUploadService.class);
        ArtifactMetadata metadata = mock(ArtifactMetadata.class);
        byte[] content = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(metadata.getStorageReference()).thenReturn("artifact://org/run/task/lease.json");
        when(metadata.getSha256()).thenReturn("a".repeat(64));
        when(metadata.getRetainUntil()).thenReturn("2026-10-23T00:00:00Z");
        when(artifacts.upload("lease", "runner", "nonce", "application/json", "a".repeat(64), content)).thenReturn(metadata);

        new RunnerArtifactController(runners, artifacts)
                .upload("runner", "credential", "lease", "nonce", "a".repeat(64), content);

        verify(runners).authenticated("runner", "credential");
        verify(artifacts).upload("lease", "runner", "nonce", "application/json", "a".repeat(64), content);
    }
}
