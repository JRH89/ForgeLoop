package io.forgeloop.control.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask,String> {
    List<DeliveryTask> findByStateIn(Collection<TaskState> states);
    List<DeliveryTask> findByRun_Id(String runId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from DeliveryTask task where task.run.id = :runId order by task.id")
    List<DeliveryTask> findAllForUpdateByRunId(@Param("runId") String runId);
}
