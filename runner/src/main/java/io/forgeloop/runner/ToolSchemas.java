package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ToolSchemas {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ToolSchemas() { }
    static JsonNode parse(String schema) {
        try { return JSON.readTree(schema); }
        catch (java.io.IOException invalid) { throw new IllegalStateException("Bundled tool schema is invalid", invalid); }
    }
    static ToolSpec spec(String name, String description, String schema) { return new ToolSpec(name, description, parse(schema)); }
}
