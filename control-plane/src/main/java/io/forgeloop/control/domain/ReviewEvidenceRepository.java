package io.forgeloop.control.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ReviewEvidenceRepository extends JpaRepository<ReviewEvidence, String> {
    Optional<ReviewEvidence> findByDigest(String digest);
    @EntityGraph(attributePaths = "criteria")
    List<ReviewEvidence> findByTask_Run_IdOrderByRecordedAtAsc(String runId);
}
