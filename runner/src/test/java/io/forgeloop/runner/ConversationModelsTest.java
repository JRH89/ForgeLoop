package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationModelsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void requestAndToolArgumentsAreDefensiveAndBounded() throws Exception {
        var schema = JSON.readTree("{\"type\":\"object\",\"properties\":{}}");
        ToolSpec tool = new ToolSpec("read_file", "Read a file", schema);
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).removeAll();
        assertEquals("object", tool.inputSchema().path("type").asText());
        var arguments = JSON.readTree("{\"path\":\"README.md\"}");
        ToolCall call = new ToolCall("call_1", "read_file", arguments);
        ((com.fasterxml.jackson.databind.node.ObjectNode) arguments).removeAll();
        assertEquals("README.md", call.arguments().path("path").asText());
        assertThrows(IllegalArgumentException.class, () -> new ToolCall("call_2", "read_file", JSON.getNodeFactory().textNode("not an object")));
        assertThrows(IllegalArgumentException.class, () -> request(List.of(new UserText("hello")), List.of(tool), Duration.ofMinutes(10).plusMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new ToolSpec("ReadFile", "bad name", JSON.createObjectNode()));
    }

    @Test void requestCopiesItemAndToolListsAndAllowsEmptyAssistantText() throws Exception {
        var replay = JSON.readTree("[{\"type\":\"thinking\",\"signature\":\"signed\"}]");
        AssistantTurn assistant = new AssistantTurn("", List.of(), replay);
        ((com.fasterxml.jackson.databind.node.ArrayNode) replay).removeAll();
        List<ConversationItem> items = new java.util.ArrayList<>(List.of(new UserText("start"), assistant));
        ConversationRequest request = request(items, List.of(), Duration.ofMinutes(5));
        items.clear();
        assertEquals(2, request.items().size());
        assertEquals("signed", ((AssistantTurn) request.items().get(1)).replay().get(0).path("signature").asText());
        assertEquals("", assistant.text());
    }

    @Test void rejectsDuplicateToolsAndInvalidTurnValues() throws Exception {
        var schema = JSON.readTree("{\"type\":\"object\"}");
        ToolSpec first = new ToolSpec("read_file", "Read", schema);
        ToolSpec second = new ToolSpec("read_file", "Read again", schema);
        assertThrows(IllegalArgumentException.class, () -> request(List.of(new UserText("hi")), List.of(first, second), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new ConversationTurn("", List.of(), StopReason.END_TURN, -1, 0, null, JSON.createArrayNode(), "{}"));
        assertThrows(IllegalArgumentException.class, () -> new ConversationRequest("model", "rules", List.of(new UserText("hi")), List.of(), 127, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new ConversationRequest("model", "rules", List.of(new UserText("hi")), List.of(), 32_769, Duration.ofSeconds(5)));
        assertEquals(32_768, new ConversationRequest("model", "rules", List.of(new UserText("hi")), List.of(), 32_768, Duration.ofMinutes(10)).maxOutputTokens());
    }

    private static ConversationRequest request(List<ConversationItem> items, List<ToolSpec> tools, Duration timeout) {
        return new ConversationRequest("model", "instructions", items, tools, 128, timeout);
    }
}
