package io.forgeloop.control.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactMetadataRepository extends JpaRepository<ArtifactMetadata, String> {
    Optional<ArtifactMetadata> findByLeaseIdAndDisplayName(String leaseId, String displayName);
    Optional<ArtifactMetadata> findByLeaseIdAndStorageReference(String leaseId, String storageReference);
    List<ArtifactMetadata> findByRunIdOrderByCreatedAtAsc(String runId);
}
