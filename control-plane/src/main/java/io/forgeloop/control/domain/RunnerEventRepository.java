package io.forgeloop.control.domain;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RunnerEventRepository extends JpaRepository<RunnerEvent,String>{
    Optional<RunnerEvent> findByLeaseIdAndSequenceNumber(String leaseId,long sequenceNumber);
    List<RunnerEvent> findByRunIdOrderByOccurredAtAscSequenceNumberAsc(String runId);
}
