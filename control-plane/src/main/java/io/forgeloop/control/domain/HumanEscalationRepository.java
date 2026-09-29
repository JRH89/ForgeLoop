package io.forgeloop.control.domain;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface HumanEscalationRepository extends JpaRepository<HumanEscalation,String>{
    List<HumanEscalation> findByRunIdOrderByCreatedAtAsc(String runId);
    @Query("select escalation from HumanEscalation escalation where escalation.runId in :runIds "
            + "and escalation.status in ('OPEN', 'ACKNOWLEDGED') order by escalation.createdAt desc")
    List<HumanEscalation> findUnresolvedByRunIds(@Param("runIds") Collection<String> runIds);
    Optional<HumanEscalation> findByRunIdAndTaskIdAndReason(String runId,String taskId,String reason);
}
