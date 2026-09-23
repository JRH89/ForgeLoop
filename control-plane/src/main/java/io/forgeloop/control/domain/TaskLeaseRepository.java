package io.forgeloop.control.domain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
/** Retains lease-attempt history while selecting only the latest attempt for task coordination. */
public interface TaskLeaseRepository extends JpaRepository<TaskLease,String>{Optional<TaskLease> findFirstByTask_IdOrderByExpiresAtDesc(String taskId); List<TaskLease> findByCompletedAtIsNullAndExpiresAtBefore(Instant now);}
