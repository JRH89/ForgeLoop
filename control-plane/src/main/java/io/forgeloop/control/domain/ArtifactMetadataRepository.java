package io.forgeloop.control.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactMetadataRepository extends JpaRepository<ArtifactMetadata, String> {
    Optional<ArtifactMetadata> findByLeaseIdAndDisplayName(String leaseId, String displayName);
    List<ArtifactMetadata> findByRunIdOrderByCreatedAtAsc(String runId);
}
