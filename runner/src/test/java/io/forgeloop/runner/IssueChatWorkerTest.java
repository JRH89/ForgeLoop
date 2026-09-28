package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssueChatWorkerTest {
    private final ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "model", 1, BigDecimal.ONE, BigDecimal.ONE);
    private final IssueChatTurnGrant grant = new IssueChatTurnGrant("chat-1", "acme/app", null, null, List.of(),
            List.of(new IssueChatTurnGrant.Message("USER", "Add a timeout to export jobs.")));

    @Test void returnsAnEditableIssueDraftAndBillableUsageWithoutARepositoryTool() throws Exception {
        String output = "{\"assistantMessage\":\"I drafted a testable issue.\",\"title\":\"Bound export job duration\","
                + "\"body\":\"Export jobs should stop after the configured timeout.\",\"acceptanceCriteria\":[\"A timed-out export reports a clear status.\"]}";
        ProviderClient provider = request -> {
            assertTrue(request.instructions().contains("Do not claim work was executed"));
            assertTrue(request.input().contains("Add a timeout to export jobs."));
            assertEquals("object", request.outputSchema().path("type").asText());
            return new ProviderResult(output, 20, 15, "request-1");
        };
        IssueChatResult result = new IssueChatWorker().execute(policy, provider, grant);
        assertEquals("I drafted a testable issue.", result.assistantMessage());
        assertEquals("Bound export job duration", result.specification().title());
        assertEquals(1, result.specification().acceptanceCriteria().size());
        assertEquals(35, result.usage().estimatedCostMicros());
    }

    @Test void invalidOutputRetainsProviderUsageForTheCostLedger() {
        ProviderClient provider = ignored -> new ProviderResult("{\"title\":\"x\"}", 20, 15, "request-2");
        IssueChatOutputFailure failure = assertThrows(IssueChatOutputFailure.class,
                () -> new IssueChatWorker().execute(policy, provider, grant));
        assertEquals(35, failure.usage().estimatedCostMicros());
    }
}
