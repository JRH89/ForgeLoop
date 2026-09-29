package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderToolCheckTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void makesTwoTurnsAndReturnsOnlyContentFreeDiagnostics() throws Exception {
        ConversationTurn first = new ConversationTurn("", List.of(new ToolCall("call-1", "echo", JSON.readTree("{\"text\":\"private echoed text\"}"))),
                StopReason.TOOL_USE, 10, 3, "first-id", JSON.readTree("[{\"type\":\"tool_use\"}]"), "private provider response");
        ConversationTurn second = new ConversationTurn("private final answer", List.of(), StopReason.END_TURN,
                8, 2, "second-id", JSON.createArrayNode(), "private final response body");
        FakeClient client = new FakeClient(first, second);

        ProviderToolCheckResult result = new ProviderToolCheck().run(client, "model", 2);

        assertEquals(2, client.requests.size());
        assertEquals("echo", client.requests.getFirst().tools().getFirst().name());
        assertEquals("object", client.requests.getFirst().tools().getFirst().inputSchema().path("type").asText());
        assertTrue(client.requests.getFirst().tools().getFirst().inputSchema().path("required").isArray());
        var resultItem = ((ToolResults) client.requests.get(1).items().get(2)).results().getFirst();
        assertEquals("private echoed text", resultItem.content());
        assertEquals(StopReason.TOOL_USE, result.firstStopReason());
        assertEquals(StopReason.END_TURN, result.secondStopReason());
        assertEquals(18, result.inputTokens());
        assertEquals(5, result.outputTokens());
        assertFalse(result.toString().contains("private"));
    }

    @Test void refusesUnexpectedToolCallsWithoutMakingTheFollowUpRequest() {
        ConversationTurn unexpected = new ConversationTurn("", List.of(new ToolCall("call-1", "shell", JSON.createObjectNode())),
                StopReason.TOOL_USE, 1, 1, null, JSON.createArrayNode(), "{}");
        FakeClient client = new FakeClient(unexpected);
        assertThrows(IllegalStateException.class, () -> new ProviderToolCheck().run(client, "model", 1));
        assertEquals(1, client.requests.size());
    }

    @Test void reportsAnUnfinishedFollowUpAsAnUnsuccessfulToolCheck() throws Exception {
        ConversationTurn first = new ConversationTurn("", List.of(new ToolCall("call-1", "echo", JSON.readTree("{\"text\":\"x\"}"))),
                StopReason.TOOL_USE, 1, 1, null, JSON.createArrayNode(), "{}");
        ConversationTurn second = new ConversationTurn("", List.of(), StopReason.REFUSAL, 1, 1, null, JSON.createArrayNode(), "{}");
        FakeClient client = new FakeClient(first, second);
        assertThrows(IllegalStateException.class, () -> new ProviderToolCheck().run(client, "model", 1));
    }

    @Test void cliCommandRequiresProviderAndModelArguments() {
        assertThrows(IllegalArgumentException.class, () -> RunnerMain.main(new String[]{"provider-tool-check"}));
    }

    private static final class FakeClient implements ConversationClient {
        private final List<ConversationRequest> requests = new ArrayList<>();
        private final List<ConversationTurn> turns;
        private int position;
        private FakeClient(ConversationTurn... turns) { this.turns = List.of(turns); }
        @Override public String serialize(ConversationRequest request) { return "{}"; }
        @Override public ConversationTurn converse(ConversationRequest request) {
            requests.add(request);
            return turns.get(position++);
        }
    }
}
