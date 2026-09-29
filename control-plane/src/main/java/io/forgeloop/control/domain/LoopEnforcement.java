package io.forgeloop.control.domain;

import java.util.List;

/** Immutable repository enforcement settings captured for one run and its agent-loop tasks. */
public record LoopEnforcement(List<String> protectedPaths, boolean allowWorkflowChanges, String finishGate) {
    public LoopEnforcement {
        protectedPaths = List.copyOf(protectedPaths == null ? List.of() : protectedPaths);
    }

    public static LoopEnforcement defaults() {
        return new LoopEnforcement(List.of(), false, null);
    }
}
