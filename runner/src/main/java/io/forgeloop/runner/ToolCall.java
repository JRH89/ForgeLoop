package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;

/** Parsed provider tool call with arguments represented only as a JSON object. */
public record ToolCall(String id, String name, JsonNode arguments) {
    public ToolCall {
        if (id == null || id.isBlank() || id.length() > 256 || name == null || !name.matches("[a-z][a-z0-9_]{0,63}")
                || arguments == null || !arguments.isObject())
            throw new IllegalArgumentException("Provider tool call is invalid");
        arguments = arguments.deepCopy();
    }

    @Override public JsonNode arguments() { return arguments.deepCopy(); }
}
