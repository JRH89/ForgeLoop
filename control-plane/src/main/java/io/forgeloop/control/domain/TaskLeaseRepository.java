package io.forgeloop.control.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TaskLeaseRepository extends JpaRepository<TaskLease,String>{Optional<TaskLease> findByTaskId(String taskId);}
