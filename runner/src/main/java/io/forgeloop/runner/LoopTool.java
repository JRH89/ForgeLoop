package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/** A single capability exposed through ToolGateway, with argument checks before execution. */
public interface LoopTool {
    ToolSpec spec();
    void validateArguments(JsonNode arguments) throws LoopToolFailure;
    ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, LoopHarnessFailure, IOException, InterruptedException;
}
