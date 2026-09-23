package io.forgeloop.control.application;

import io.forgeloop.control.domain.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies the fail-closed escalation policy and owns operator acknowledgement. */
@Service
public class HumanEscalationService {
    private final HumanEscalationRepository escalations;
    public HumanEscalationService(HumanEscalationRepository escalations){this.escalations=escalations;}
    @Transactional public HumanEscalation escalate(DeliveryTask task,String reason,String summary){
        FeatureRun run=task.getRun();
        HumanEscalation existing=escalations.findByRunIdAndTaskIdAndReason(run.getId(),task.getId(),reason).orElse(null);
        if(existing!=null){existing.raiseAgain(summary);return existing;}
        return escalations.save(new HumanEscalation(run,task,reason,severity(reason),summary));
    }
    public List<HumanEscalation> list(String runId){return escalations.findByRunIdOrderByCreatedAtAsc(runId);}
    @Transactional public HumanEscalation acknowledge(String id,String organization,String actor){HumanEscalation item=requireOwned(id,organization);item.acknowledge(actor);return item;}
    @Transactional public HumanEscalation resolve(String id,String organization,String actor){HumanEscalation item=requireOwned(id,organization);item.resolve(actor);return item;}
    private HumanEscalation requireOwned(String id,String organization){HumanEscalation item=escalations.findById(id).orElseThrow(()->new IllegalArgumentException("Escalation not found"));if(!item.belongsTo(organization))throw new IllegalArgumentException("Escalation not found");return item;}
    private String severity(String reason){return List.of("BUDGET_EXHAUSTED","ATTEMPT_BUDGET_EXHAUSTED").contains(reason)?"HIGH":"MEDIUM";}
}
