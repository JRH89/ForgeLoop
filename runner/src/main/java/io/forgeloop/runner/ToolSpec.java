package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;

/** Tool declaration sent to a provider. Runtime argument validation is performed by the runner gateway. */
public record ToolSpec(String name, String description, JsonNode inputSchema) {
    public ToolSpec {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,63}") || description == null || description.isBlank()
                || inputSchema == null || !inputSchema.isObject())
            throw new IllegalArgumentException("Tool specification is invalid");
        inputSchema = inputSchema.deepCopy();
    }

    @Override public JsonNode inputSchema() { return inputSchema.deepCopy(); }
}
