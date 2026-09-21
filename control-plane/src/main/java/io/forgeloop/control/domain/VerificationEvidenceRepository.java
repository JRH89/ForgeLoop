package io.forgeloop.control.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationEvidenceRepository extends JpaRepository<VerificationEvidence, String> {
    List<VerificationEvidence> findByTask_IdOrderByRecordedAtAsc(String taskId);
    java.util.Optional<VerificationEvidence> findFirstByTask_IdOrderByRecordedAtDesc(String taskId);
}
