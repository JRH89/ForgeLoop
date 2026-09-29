package io.forgeloop.control.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Queries immutable test-check evidence by runner task and run scope. */
public interface TestCheckEvidenceRepository extends JpaRepository<TestCheckEvidence, String> {
    Optional<TestCheckEvidence> findByDigest(String digest);
    List<TestCheckEvidence> findByTask_Run_IdOrderByRecordedAtAsc(String runId);
    List<TestCheckEvidence> findByTask_IdOrderByRecordedAtAsc(String taskId);
}
