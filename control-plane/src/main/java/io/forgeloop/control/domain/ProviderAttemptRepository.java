package io.forgeloop.control.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderAttemptRepository extends JpaRepository<ProviderAttempt, String> {
    Optional<ProviderAttempt> findByTask_IdAndRequestIdDigest(String taskId, String requestIdDigest);
    Optional<ProviderAttempt> findFirstByTask_IdOrderByRecordedAtDesc(String taskId);
    List<ProviderAttempt> findByTask_IdOrderByRecordedAtAsc(String taskId);
    @Query("select coalesce(sum(attempt.estimatedCostMicros), 0) from ProviderAttempt attempt where attempt.task.id = :taskId and attempt.costKnown = true")
    long sumKnownCostByTaskId(@Param("taskId") String taskId);
    @Query("select coalesce(sum(attempt.estimatedCostMicros), 0) from ProviderAttempt attempt where attempt.task.run.id = :runId and attempt.costKnown = true")
    long sumKnownCostByRunId(@Param("runId") String runId);
}
