package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Strict JSON task result contract; prose or malformed provider output is never treated as a patch. */
public record PatchPlan(String summary, List<ProposedChange> changes) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public PatchPlan { if (summary == null || summary.isBlank() || changes == null || changes.isEmpty() || changes.size() > 20) throw new IllegalArgumentException("Patch plan is incomplete"); changes = List.copyOf(changes); }
    public static PatchPlan parse(String output) {
        try {
            JsonNode root = JSON.readTree(output); List<ProposedChange> changes = new ArrayList<>();
            for (JsonNode change : root.path("changes")) changes.add(new ProposedChange(change.path("path").asText(), change.path("content").asText(), change.path("message").asText()));
            return new PatchPlan(root.path("summary").asText(), changes);
        } catch (IllegalArgumentException exception) { throw exception;
        } catch (Exception exception) { throw new IllegalArgumentException("Provider patch plan is not valid JSON", exception); }
    }
}
