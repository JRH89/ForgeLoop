package io.forgeloop.runner;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, task-scoped authority passed to every tool and interceptor. */
public record ToolContext(String taskId, String leaseId, String role, Path worktree, Set<String> declaredTools,
                          List<String> ownedPrefixes, List<LoopGate> gates, int turn, int step,
                          long remainingWallMillis, LoopCounters counters, Map<String, ToolOutcome> gateOutcomes,
                          String notExecutedReason, String baseSha, int lastWriteStep) {
    public ToolContext {
        if (taskId == null || taskId.isBlank() || leaseId == null || leaseId.isBlank() || role == null || worktree == null
                || declaredTools == null || ownedPrefixes == null || gates == null || turn < 1 || step < 1 || remainingWallMillis < 0 || counters == null
                || baseSha == null || !baseSha.matches("[0-9a-f]{40,64}") || lastWriteStep < 0)
            throw new IllegalArgumentException("Tool context is invalid");
        if (notExecutedReason != null && (notExecutedReason.isBlank() || notExecutedReason.length() > 200))
            throw new IllegalArgumentException("Tool skip reason is invalid");
        WorkerRolePolicy.require(role);
        worktree = worktree.toAbsolutePath().normalize();
        declaredTools = Set.copyOf(declaredTools);
        ownedPrefixes = List.copyOf(ownedPrefixes);
        gates = List.copyOf(gates);
        gateOutcomes = Map.copyOf(gateOutcomes == null ? Map.of() : gateOutcomes);
    }

    public ToolContext(String taskId, String leaseId, String role, Path worktree, Set<String> declaredTools,
                       List<String> ownedPrefixes, List<LoopGate> gates, int turn, int step,
                       long remainingWallMillis, LoopCounters counters, Map<String, ToolOutcome> gateOutcomes,
                       String notExecutedReason) {
        this(taskId, leaseId, role, worktree, declaredTools, ownedPrefixes, gates, turn, step, remainingWallMillis,
                counters, gateOutcomes, notExecutedReason, "0".repeat(40), 0);
    }
}
