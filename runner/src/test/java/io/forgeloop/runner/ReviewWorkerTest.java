package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReviewWorkerTest {
    @Test void reviewSchemaPinsExactStatementsAndCount() {
        List<String> statements = List.of("Search.search('   ') returns []", "`npm run build` succeeds");
        var schema = StructuredOutputSchemas.review(statements);
        var criteria = schema.path("properties").path("criteria");
        assertEquals(2, criteria.path("minItems").asInt());
        assertEquals(2, criteria.path("maxItems").asInt());
        assertEquals("Search.search('   ') returns []", criteria.path("items").path("properties")
                .path("statement").path("enum").get(0).asText());
        assertThrows(IllegalArgumentException.class, () -> StructuredOutputSchemas.review(List.of("same", "same")));
    }
    @Test void suppliesFullReturnContractBeyondDiffHunks() throws Exception {
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        ProviderClient provider = request -> {
            captured.set(request);
            return new ProviderResult("{\"approved\":true,\"summary\":\"Contract preserved\",\"criteria\":[{\"statement\":\"Authorization is enforced\",\"status\":\"PASS\",\"evidence\":\"Guard remains\"}]}", 10, 4, "source-review");
        };
        String source = "return results.map(result => result.item);";
        new ReviewWorker().execute(new ProviderExecutionPolicy("anthropic", "model", 1), provider, task,
                "partial diff", "source-review", source);
        assertTrue(captured.get().input().contains(source));
        assertTrue(captured.get().instructions().contains("Trace complete return values"));
        assertTrue(captured.get().instructions().contains("verification runs independently"));
        assertTrue(captured.get().instructions().contains("execution is pending"));
        assertTrue(captured.get().instructions().contains("static approval cannot authorize delivery without it"));
    }
    private final RunnerTask task=new RunnerTask("task-1","REVIEW","Independent review","acme/widget","main","issue-1","Must remain authorized","provider",10,List.of(),List.of("a".repeat(40)),null,null,null,List.of(),null,null,"main","main",List.of("Authorization is enforced"));
    @Test void acceptsStrictReadOnlyDecision()throws Exception{ProviderClient provider=ignored->new ProviderResult("{\"approved\":true,\"summary\":\"All criteria are covered\",\"criteria\":[{\"statement\":\"Authorization is enforced\",\"status\":\"PASS\",\"evidence\":\"Guard remains in service\"}]}",10,4,"request-1");ReviewResult result=new ReviewWorker().execute(new ProviderExecutionPolicy("anthropic","model",1),provider,task,"diff --git a/a b/a","review-1");assertTrue(result.approved());assertEquals("All criteria are covered",result.summary());assertEquals(1,result.criteria().size());}
    @Test void rejectsUnstructuredReviewOutput(){ProviderClient provider=ignored->new ProviderResult("looks good",1,1,"request-2");assertThrows(GuardedPatchFailure.class,()->new ReviewWorker().execute(new ProviderExecutionPolicy("anthropic","model",1),provider,task,"diff","review-2"));}
    @Test void includesOnlyTheServerProvidedTestEvidenceAndItsReviewInstruction()throws Exception{
        RunnerTask withEvidence=new RunnerTask("task-1","REVIEW","Independent review","acme/widget","main","issue-1",
                "Must remain authorized","provider",10,List.of(),List.of("a".repeat(40)),null,null,null,List.of(),null,null,
                "main","main",List.of("Authorization is enforced"),List.of(),"ANY",List.of(),null,List.of(),false,
                "3 new tests fail before the implementation and pass at the integrated head.");
        AtomicReference<ProviderRequest> captured=new AtomicReference<>();
        ProviderClient provider=request->{captured.set(request);return new ProviderResult("{\"approved\":true,\"summary\":\"All criteria are covered\",\"criteria\":[{\"statement\":\"Authorization is enforced\",\"status\":\"PASS\",\"evidence\":\"Guard remains in service\"}]}",10,4,"request-3");};

        new ReviewWorker().execute(new ProviderExecutionPolicy("anthropic","model",1),provider,withEvidence,"diff","review-3");

        assertTrue(captured.get().input().contains("Test-first evidence recorded by ForgeLoop"));
        assertTrue(captured.get().input().contains("3 new tests fail before the implementation"));
        assertTrue(captured.get().instructions().contains("fail a criterion that no added test exercises"));
    }
}
