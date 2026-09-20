package io.forgeloop.control.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
/** Lease lookup follows the task entity relationship rather than a nonexistent scalar taskId column. */
public interface TaskLeaseRepository extends JpaRepository<TaskLease,String>{Optional<TaskLease> findByTask_Id(String taskId);}
