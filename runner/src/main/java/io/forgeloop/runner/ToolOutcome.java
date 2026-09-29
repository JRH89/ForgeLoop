package io.forgeloop.runner;

import java.util.List;
import java.util.Map;

/** Full local tool result; only the shaped content is sent back to the provider. */
public record ToolOutcome(ToolStatus status, FailureCategory category, String content,
                          Map<String, Object> meta, List<PostImage> postImages) {
    public ToolOutcome {
        if (status == null || content == null || (status == ToolStatus.OK) != (category == null))
            throw new IllegalArgumentException("Tool outcome is invalid");
        meta = Map.copyOf(meta == null ? Map.of() : meta);
        postImages = List.copyOf(postImages == null ? List.of() : postImages);
    }

    public static ToolOutcome ok(String content) { return new ToolOutcome(ToolStatus.OK, null, content, Map.of(), List.of()); }
    public static ToolOutcome ok(String content, Map<String, Object> meta, List<PostImage> images) {
        return new ToolOutcome(ToolStatus.OK, null, content, meta, images);
    }
    public static ToolOutcome failed(FailureCategory category, String content) {
        return new ToolOutcome(ToolStatus.FAILED, category, content, Map.of(), List.of());
    }

    public static ToolOutcome held(HoldClass holdClass, String rule, String reason) {
        return new ToolOutcome(ToolStatus.FAILED, FailureCategory.PERMISSION,
                "Not executed: this task is now held for a person (" + holdClass.name() + ").",
                Map.of("decision", "HOLD", "holdClass", holdClass.name(), "holdRule", rule, "holdReason", reason), List.of());
    }
}
