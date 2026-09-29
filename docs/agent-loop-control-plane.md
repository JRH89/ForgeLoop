# Agent-loop control-plane contract

Slice 3c adds the control-plane contract for a future long-running agent loop. It is additive and opt-in: existing repositories and tasks remain on the current single-call runner path, and this slice does not dispatch or execute a loop.

## Repository policy

An organization administrator configures a connected repository through `configureRepositoryAgentLoop(repository, budget)`. Supply all four bounded limits to enable the policy, or pass `null` to disable it. The mutation advances the repository policy revision and writes a metadata-only audit entry. Every enabled policy is range-checked in Java and by database constraints:

| Limit | Allowed range |
| --- | ---: |
| `maxToolCalls` | 1–1,000 |
| `maxTokens` | 10,000–100,000,000 |
| `maxWallSeconds` | 60–14,400 |
| `maxConversationBytes` | 65,536–4,194,304 |

When a run is submitted, it copies the repository's loop budget and verification gates. Later repository changes do not alter that run. `Task.agentLoop` is non-null only for a snapshotted, source-writing task; planner, integration, review, verification, and unconfigured tasks receive `null`. Only fully specified, digest-pinned gates are included in the loop payload.

The administrator-only `configureRepositoryEnforcement(repository, input)` mutation stores an optional list of protected path globs, an audited opt-out for the built-in `.github/workflows/**` deny, and an optional finish gate that must name one of the repository's verification policies. All three settings are copied into a submitted run and exposed as `Task.agentLoop.enforcement`; existing runs retain their policy snapshot when repository settings change. Custom protected paths use the case-sensitive repository glob language. The workflow opt-out does not remove custom globs, and the finish gate is advisory—it cannot create or replace official verification evidence. See [Agent-loop enforcement](agent-loop-enforcement.md) for rule behavior.

## Runner lease lifecycle

The runner-facing `renewTaskLease` and `holdTaskLease` mutations require the runner credential, lease id, and nonce. Both require an active, acknowledged lease and an eligible task from a loop-enabled run.

- Renewal extends the lease by at most ten minutes per call and never beyond `claimedAt + maxWallSeconds + 15 minutes`. A cancelled, terminal, held, expired, or non-loop task cannot renew.
- Hold accepts only `LOOP_BUDGET_EXHAUSTED`, `BUDGET_EXHAUSTED`, or `WORKER_DECLINED`, plus a non-empty, redacted summary of at most 1,000 characters. It closes the lease, holds the task, blocks the run, and creates a human escalation. Loop-budget exhaustion is high severity.
- If cancellation has already held the task, a late hold only closes the lease; it does not block or reopen the cancelled run or create a duplicate escalation.
- Existing `retryFeatureTask` is the operator recovery path for held work; retrying gives the task a fresh execution attempt under existing retry rules.

`completeTaskLease` accepts an optional `category` for general execution failures. Verification and review failures keep their existing server-selected categories. Omitting the argument preserves existing behavior.

The event allow-list now recognizes `LOOP_STARTED`, `LOOP_TOOL_CALLED`, `LOOP_ENDED`, and `LOOP_RESUMED`. This slice defines the accepted metadata event types but does not emit them; emission and lease-keeper wiring remain for slice 3d.

## GraphQL examples

Enable a repository policy:

```graphql
mutation {
  configureRepositoryAgentLoop(
    repository: "owner/repository"
    budget: {
      maxToolCalls: 80
      maxTokens: 500000
      maxWallSeconds: 1800
      maxConversationBytes: 1048576
    }
  ) {
    repository
    policyRevision
    agentLoop { maxToolCalls maxTokens maxWallSeconds maxConversationBytes }
  }
}
```

Disable it by passing `budget: null`. Existing runs retain their snapshotted policy either way.
