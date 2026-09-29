package io.forgeloop.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Repository enforcement authority copied from the server's snapshotted task policy. */
public record RunnerLoopEnforcement(List<String> protectedPaths, Boolean allowWorkflowChanges, String finishGate) {
    public RunnerLoopEnforcement {
        protectedPaths = protectedPaths == null ? null
                : Collections.unmodifiableList(new ArrayList<>(protectedPaths));
    }
}
