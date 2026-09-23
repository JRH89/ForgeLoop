package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewWorkerTest {
    private final RunnerTask task=new RunnerTask("task-1","REVIEW","Independent review","acme/widget","main","issue-1","Must remain authorized","provider",10,List.of(),List.of("a".repeat(40)),null,null,null,List.of(),null,null,"main","main",List.of("Authorization is enforced"));
    @Test void acceptsStrictReadOnlyDecision()throws Exception{ProviderClient provider=ignored->new ProviderResult("{\"approved\":true,\"summary\":\"All criteria are covered\",\"criteria\":[{\"statement\":\"Authorization is enforced\",\"status\":\"PASS\",\"evidence\":\"Guard remains in service\"}]}",10,4,"request-1");ReviewResult result=new ReviewWorker().execute(new ProviderExecutionPolicy("anthropic","model",1),provider,task,"diff --git a/a b/a","review-1");assertTrue(result.approved());assertEquals("All criteria are covered",result.summary());assertEquals(1,result.criteria().size());}
    @Test void rejectsUnstructuredReviewOutput(){ProviderClient provider=ignored->new ProviderResult("looks good",1,1,"request-2");assertThrows(GuardedPatchFailure.class,()->new ReviewWorker().execute(new ProviderExecutionPolicy("anthropic","model",1),provider,task,"diff","review-2"));}
}
