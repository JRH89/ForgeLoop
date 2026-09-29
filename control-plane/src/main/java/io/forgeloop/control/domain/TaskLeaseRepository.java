package io.forgeloop.control.domain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
/** Retains lease-attempt history while selecting only the latest attempt for task coordination. */
public interface TaskLeaseRepository extends JpaRepository<TaskLease,String>{
    Optional<TaskLease> findFirstByTask_IdOrderByExpiresAtDesc(String taskId);
    List<TaskLease> findByCompletedAtIsNullAndExpiresAtBefore(Instant now);
    long countByTask_Run_IdAndCompletedAtIsNullAndExpiresAtAfter(String runId,Instant now);
    /** Serializes evidence retries on one lease so simultaneous submissions remain idempotent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lease from TaskLease lease where lease.id = :leaseId")
    Optional<TaskLease> findByIdForEvidenceUpdate(@Param("leaseId") String leaseId);
}
