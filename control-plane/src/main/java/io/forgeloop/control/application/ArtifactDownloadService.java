package io.forgeloop.control.application;

import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.security.OperatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Retrieves an immutable artifact only after tenant ownership and checksum validation. */
@Service
public class ArtifactDownloadService {
    private final ArtifactMetadataRepository metadata;
    private final ArtifactStore store;
    private final OperatorContext operators;
    private final long maxBytes;

    public ArtifactDownloadService(ArtifactMetadataRepository metadata, ArtifactStore store, OperatorContext operators,
                                   @Value("${forgeloop.artifacts.max-bytes:1048576}") long maxBytes) {
        this.metadata = metadata; this.store = store; this.operators = operators; this.maxBytes = maxBytes;
    }

    public Download download(String id) {
        ArtifactMetadata artifact = metadata.findById(id).orElseThrow(() -> new IllegalArgumentException("Artifact not found"));
        if (!artifact.getOrganizationId().equals(operators.organizationId())) throw new IllegalArgumentException("Artifact not found");
        String prefix = "artifact://";
        if (!artifact.getStorageReference().startsWith(prefix)) throw new IllegalStateException("Artifact reference is invalid");
        byte[] content = store.getVerified(artifact.getStorageReference().substring(prefix.length()), artifact.getSha256(), maxBytes);
        return new Download(artifact.getContentType(), artifact.getDisplayName(), content);
    }

    public record Download(String contentType, String displayName, byte[] content) {
        public Download { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
