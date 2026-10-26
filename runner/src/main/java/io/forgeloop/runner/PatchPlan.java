package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.util.ArrayList;
import java.util.List;

/** Strict JSON task result contract; prose or malformed provider output is never treated as a patch. */
public record PatchPlan(String summary, List<ProposedChange> changes) {
    static final int MAX_SUMMARY_LENGTH = 500;
    static final int MAX_CHANGES = 20;
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public PatchPlan {
        if (summary == null || summary.isBlank() || summary.length() > MAX_SUMMARY_LENGTH || changes == null || changes.isEmpty() || changes.size() > MAX_CHANGES) throw new IllegalArgumentException("Patch plan is incomplete");
        if (changes.stream().map(ProposedChange::path).distinct().count() != changes.size()) throw new IllegalArgumentException("Patch plan contains duplicate paths");
        changes = List.copyOf(changes);
    }
    public static PatchPlan parse(String output) { return parse(output, false); }

    /** Canonicalize commit prose only; proposed source, paths and messages retain strict validation. */
    static PatchPlan parseForWorker(String output) { return parse(output, true); }

    private static PatchPlan parse(String output, boolean normalizeMetadata) {
        try {
            JsonNode root = JSON.readTree(output);
            if (!root.isObject() || !hasExactly(root, "summary", "changes") || !root.path("summary").isTextual() || !root.path("changes").isArray()) throw new IllegalArgumentException("Provider patch plan does not match its schema");
            List<ProposedChange> changes = new ArrayList<>();
            for (JsonNode change : root.path("changes")) {
                if (!change.isObject() || !hasExactly(change, "path", "content", "message") || !change.path("path").isTextual() || !change.path("content").isTextual() || !change.path("message").isTextual()) throw new IllegalArgumentException("Provider change does not match its schema");
                changes.add(new ProposedChange(change.path("path").asText(), change.path("content").asText(), change.path("message").asText()));
            }
            String summary = root.path("summary").asText();
            return new PatchPlan(normalizeMetadata ? boundedSummary(summary) : summary, changes);
        } catch (IllegalArgumentException exception) { throw exception;
        } catch (Exception exception) { throw new IllegalArgumentException("Provider patch plan is not valid JSON", exception); }
    }
    private static String boundedSummary(String summary) {
        String normalized = summary.replaceAll("[\\r\\n]+", " ").strip();
        int end = Math.min(normalized.length(), MAX_SUMMARY_LENGTH);
        if (end > 0 && end < normalized.length() && Character.isHighSurrogate(normalized.charAt(end - 1))
                && Character.isLowSurrogate(normalized.charAt(end))) end--;
        return normalized.substring(0, end);
    }

    private static boolean hasExactly(JsonNode node, String... names) {
        java.util.Set<String> expected = java.util.Set.of(names); java.util.Set<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add); return actual.equals(expected);
    }
}
