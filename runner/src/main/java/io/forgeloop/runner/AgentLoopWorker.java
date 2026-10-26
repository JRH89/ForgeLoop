package io.forgeloop.runner;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dispatch adapter for the existing guarded loop; official verification still runs in separate tasks. */
final class AgentLoopWorker {
    private final StageSkillResolver skills;
    AgentLoopWorker() { this(StageSkillResolver.fromEnvironment()); }
    AgentLoopWorker(StageSkillResolver skills) {
        if (skills == null) throw new IllegalArgumentException("Skill resolver is required");
        this.skills = skills;
    }
    static boolean enabled(RunnerTask task, boolean runnerEnabled, boolean toolCalling) {
        if (!runnerEnabled || task.agentLoop() == null) return false;
        if (!GuardedPatchWorker.supports(task.role()) || !toolCalling)
            throw new IllegalArgumentException("Agent-loop dispatch requires a writing role and tool-calling provider policy");
        return true;
    }

    LoopResult execute(RunnerTask task, ProviderExecutionPolicy policy, ConversationClient provider,
                       Path worktree, String leaseId, LoopJournal journal, LoopReporter reporter,
                       SpendReservationClient reservations, LeaseKeeper.Renewal renewal,
                       RunGateExecutor gateExecutor) throws Exception {
        RunnerLoopConfiguration config = task.agentLoop();
        if (config == null) throw new IllegalArgumentException("Agent-loop task policy is missing");
        SkillSelection selection = skills.resolveRole(task.role(), task.repository());
        GitWorktreeManager git = new GitWorktreeManager();
        AtomicBoolean lost = new AtomicBoolean();
        ToolRegistry registry = ToolRegistry.standard(git, gateExecutor);
        List<String> changed = git.changedFiles(worktree, RunnerMain.checkoutRef(task, task.baseBranch()));
        LoopSetup setup = new LoopSetup(task.id(), task.repository(), leaseId, task.role(), task.title(),
                task.specification(), task.ownedPaths(), changed, worktree, git.headSha(worktree), policy, provider,
                config.budget(), config.gates(), new ToolGateway(registry, journal, List.of()), registry,
                journal, Clock.systemUTC(), reporter, lost, config.budgetMicros(), config.spentCostMicros(),
                EnforcementDescriptor.of(task, config.gates()), reservations);
        LoopResult result;
        LeaseKeeper keeper = new LeaseKeeper(renewal, lost, Duration.ofSeconds(30));
        try (keeper) { result = new AgentLoop(selection).run(setup); }
        // Revalidate before publishing a commit, including cancellation during a final provider/tool call.
        if (result.outcome() == LoopOutcome.FINISHED) keeper.renewNow();
        if (lost.get() && result.outcome() != LoopOutcome.LEASE_LOST)
            return new LoopResult(LoopOutcome.LEASE_LOST, null, result.counters(), null, null, 0);
        return result;
    }

    /** Stable reasons are server allow-listed; raw provider text never becomes an escalation summary. */
    static String holdReason(LoopResult result) {
        return switch (result.outcome()) {
            case POLICY_HOLD -> "ENFORCEMENT_" + result.holdClass().name();
            case BUDGET_STOP -> result.budgetKind() == BudgetKind.MONEY ? "BUDGET_EXHAUSTED" : "LOOP_BUDGET_EXHAUSTED";
            case DECLINED, REFUSED -> "WORKER_DECLINED";
            default -> null;
        };
    }
}
