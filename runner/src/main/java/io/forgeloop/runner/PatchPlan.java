package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.util.ArrayList;
import java.util.List;

/** Strict JSON task result contract; prose or malformed provider output is never treated as a patch. */
public record PatchPlan(String summary, List<ProposedChange> changes) {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public PatchPlan {
        if (summary == null || summary.isBlank() || summary.length() > 500 || changes == null || changes.isEmpty() || changes.size() > 20) throw new IllegalArgumentException("Patch plan is incomplete");
        if (changes.stream().map(ProposedChange::path).distinct().count() != changes.size()) throw new IllegalArgumentException("Patch plan contains duplicate paths");
        changes = List.copyOf(changes);
    }
    public static PatchPlan parse(String output) {
        try {
            JsonNode root = JSON.readTree(output);
            if (!root.isObject() || !hasExactly(root, "summary", "changes") || !root.path("summary").isTextual() || !root.path("changes").isArray()) throw new IllegalArgumentException("Provider patch plan does not match its schema");
            List<ProposedChange> changes = new ArrayList<>();
            for (JsonNode change : root.path("changes")) {
                if (!change.isObject() || !hasExactly(change, "path", "content", "message") || !change.path("path").isTextual() || !change.path("content").isTextual() || !change.path("message").isTextual()) throw new IllegalArgumentException("Provider change does not match its schema");
                changes.add(new ProposedChange(change.path("path").asText(), change.path("content").asText(), change.path("message").asText()));
            }
            return new PatchPlan(root.path("summary").asText(), changes);
        } catch (IllegalArgumentException exception) { throw exception;
        } catch (Exception exception) { throw new IllegalArgumentException("Provider patch plan is not valid JSON", exception); }
    }
    private static boolean hasExactly(JsonNode node, String... names) {
        java.util.Set<String> expected = java.util.Set.of(names); java.util.Set<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add); return actual.equals(expected);
    }
}
