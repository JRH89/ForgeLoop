package io.forgeloop.control.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewEvidenceRepository extends JpaRepository<ReviewEvidence, String> {
    Optional<ReviewEvidence> findByDigest(String digest);
    List<ReviewEvidence> findByTask_Run_IdOrderByRecordedAtAsc(String runId);
}
