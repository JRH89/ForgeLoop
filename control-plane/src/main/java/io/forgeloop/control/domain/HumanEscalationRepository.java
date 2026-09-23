package io.forgeloop.control.domain;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HumanEscalationRepository extends JpaRepository<HumanEscalation,String>{
    List<HumanEscalation> findByRunIdOrderByCreatedAtAsc(String runId);
    Optional<HumanEscalation> findByRunIdAndTaskIdAndReason(String runId,String taskId,String reason);
}
