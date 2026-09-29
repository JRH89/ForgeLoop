package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Extracts only parser-consumed JSON paths and value kinds; never retains provider response content. */
public final class ProviderShape {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, List<String>> PATHS = Map.of(
            "anthropic", List.of("id", "model", "stop_reason", "content", "content[].type", "content[].text",
                    "content[].id", "content[].name", "content[].input", "usage.input_tokens", "usage.output_tokens"),
            "openai", List.of("id", "model", "status", "output", "output[].type", "output[].content",
                    "output[].content[].type", "output[].content[].text", "output[].call_id", "output[].name",
                    "output[].arguments", "incomplete_details.reason", "usage.input_tokens", "usage.output_tokens"),
            "local", List.of("id", "model", "choices", "choices[].finish_reason", "choices[].message.role",
                    "choices[].message.content", "choices[].message.tool_calls", "choices[].message.tool_calls[].id",
                    "choices[].message.tool_calls[].type", "choices[].message.tool_calls[].function.name",
                    "choices[].message.tool_calls[].function.arguments", "usage.prompt_tokens", "usage.completion_tokens"),
            "gemini", List.of("responseId", "modelVersion", "candidates", "candidates[].finishReason",
                    "candidates[].content.role", "candidates[].content.parts", "candidates[].content.parts[].text",
                    "candidates[].content.parts[].functionCall.id", "candidates[].content.parts[].functionCall.name",
                    "candidates[].content.parts[].functionCall.args", "usageMetadata.promptTokenCount",
                    "usageMetadata.candidatesTokenCount", "usageMetadata.thoughtsTokenCount", "promptFeedback.blockReason"));

    private final Map<String, String> kinds;

    private ProviderShape(Map<String, String> kinds) { this.kinds = Collections.unmodifiableMap(new TreeMap<>(kinds)); }

    public static ProviderShape of(String provider, String responseBody) {
        List<String> paths = PATHS.get(provider);
        if (paths == null) throw new IllegalArgumentException("Unsupported provider policy");
        final JsonNode root;
        try { root = JSON.readTree(responseBody); }
        catch (Exception malformed) { throw new IllegalArgumentException("Provider response is not valid JSON", malformed); }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Provider response must be a JSON object");
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : paths) {
            List<JsonNode> values = new ArrayList<>();
            collect(root, path.split("\\."), 0, values);
            result.put(path, values.isEmpty() ? "missing" : values.stream().map(ProviderShape::kind).distinct().sorted()
                    .reduce((left, right) -> left + "|" + right).orElse("missing"));
        }
        return new ProviderShape(result);
    }

    /** Compares adapter paths only and intentionally excludes all response values. */
    public List<String> differences(ProviderShape actual) {
        if (actual == null) throw new IllegalArgumentException("Actual provider shape is required");
        List<String> differences = new ArrayList<>();
        for (Map.Entry<String, String> expected : kinds.entrySet()) {
            String actualKind = actual.kinds.getOrDefault(expected.getKey(), "missing");
            if (!expected.getValue().equals(actualKind))
                differences.add(expected.getKey() + " (expected " + expected.getValue() + ", got " + actualKind + ")");
        }
        return List.copyOf(differences);
    }

    public Map<String, String> kinds() { return kinds; }

    private static void collect(JsonNode node, String[] segments, int index, List<JsonNode> values) {
        if (index >= segments.length) { values.add(node); return; }
        String segment = segments[index];
        boolean wildcard = segment.endsWith("[]");
        String property = wildcard ? segment.substring(0, segment.length() - 2) : segment;
        JsonNode child = node.path(property);
        if (child.isMissingNode() || child.isNull()) return;
        if (wildcard) {
            if (!child.isArray()) return;
            for (JsonNode element : child) collect(element, segments, index + 1, values);
        } else collect(child, segments, index + 1, values);
    }

    private static String kind(JsonNode node) {
        if (node == null || node.isMissingNode()) return "missing";
        if (node.isNull()) return "null";
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        return "other";
    }
}
