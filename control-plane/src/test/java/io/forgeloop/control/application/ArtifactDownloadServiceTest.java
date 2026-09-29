package io.forgeloop.control.application;

import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.security.OperatorContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ArtifactDownloadServiceTest {
    @Test void runJournalDownloadRequiresOperatorRole() {
        var metadata = mock(ArtifactMetadataRepository.class);
        var store = mock(ArtifactStore.class);
        var operators = mock(OperatorContext.class);
        var artifact = artifact("RUN_JOURNAL");
        when(metadata.findById("artifact-id")).thenReturn(Optional.of(artifact));
        when(operators.organizationId()).thenReturn("org-1");
        doThrow(new org.springframework.security.access.AccessDeniedException("Denied"))
                .when(operators).requireOperator();

        var service = new ArtifactDownloadService(metadata, store, operators, 1024);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.download("artifact-id"));
        verifyNoInteractions(store);
    }

    @Test void regularEvidenceRemainsAvailableToOrganizationMembers() {
        var metadata = mock(ArtifactMetadataRepository.class);
        var store = mock(ArtifactStore.class);
        var operators = mock(OperatorContext.class);
        var artifact = artifact("TEST_LOG");
        when(metadata.findById("artifact-id")).thenReturn(Optional.of(artifact));
        when(operators.organizationId()).thenReturn("org-1");
        when(store.getVerified("objects/evidence", "abc123", 1024)).thenReturn(new byte[]{1, 2, 3});

        var result = new ArtifactDownloadService(metadata, store, operators, 1024).download("artifact-id");

        assertArrayEquals(new byte[]{1, 2, 3}, result.content());
        verify(operators, never()).requireOperator();
    }

    private static ArtifactMetadata artifact(String type) {
        var artifact = mock(ArtifactMetadata.class);
        when(artifact.getOrganizationId()).thenReturn("org-1");
        when(artifact.getArtifactType()).thenReturn(type);
        when(artifact.getStorageReference()).thenReturn("artifact://objects/evidence");
        when(artifact.getSha256()).thenReturn("abc123");
        when(artifact.getContentType()).thenReturn("application/octet-stream");
        when(artifact.getDisplayName()).thenReturn("evidence.bin");
        return artifact;
    }
}
