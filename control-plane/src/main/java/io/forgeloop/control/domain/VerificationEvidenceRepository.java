package io.forgeloop.control.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationEvidenceRepository extends JpaRepository<VerificationEvidence, String> {
    java.util.Optional<VerificationEvidence> findByDigest(String digest);
    List<VerificationEvidence> findByTask_IdOrderByRecordedAtAsc(String taskId);
    List<VerificationEvidence> findByTask_Run_IdOrderByRecordedAtAsc(String runId);
    java.util.Optional<VerificationEvidence> findFirstByTask_IdOrderByRecordedAtDesc(String taskId);
}
