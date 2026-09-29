package io.forgeloop.control.domain;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
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
    /** Closed attempt history used to derive run outcomes without loading leases one task at a time. */
    @Query("select lease from TaskLease lease join fetch lease.task task "
            + "where task.run.id in :runIds and lease.completedAt is not null order by lease.claimedAt desc")
    List<TaskLease> findClosedByRunIds(@Param("runIds") Collection<String> runIds);
    long countByTask_Run_IdAndCompletedAtIsNullAndExpiresAtAfter(String runId,Instant now);
    /** Only unexpired, incomplete sibling leases reserve run budget; retries replace their own reservation. */
    @Query("select coalesce(sum(lease.reservedMicros), 0) from TaskLease lease "
            + "where lease.task.run.id = :runId and lease.id <> :excludedLeaseId "
            + "and lease.completedAt is null and lease.expiresAt > :now")
    long sumActiveReservationsByRunExcludingLease(@Param("runId") String runId,
                                                  @Param("excludedLeaseId") String excludedLeaseId,
                                                  @Param("now") Instant now);
    /** Serializes evidence retries on one lease so simultaneous submissions remain idempotent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lease from TaskLease lease where lease.id = :leaseId")
    Optional<TaskLease> findByIdForEvidenceUpdate(@Param("leaseId") String leaseId);
}
