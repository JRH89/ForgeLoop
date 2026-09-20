# Verification protocol

Required gates are selected per repository by its versioned `RepositoryPolicy` or harness profile. The following are the Ticketly sample profile gates for FEATURE-142; they are not ForgeLoop defaults for every repository:

1. Backend compile and unit/integration tests (`mvn verify`).
2. Frontend unit tests, lint, TypeScript check, and production build (`npm run check`).
3. GraphQL contract scenario: valid in-organization assignment persists and emits an audit event.
4. Authorization scenario: cross-organization assignment is rejected by the server.
5. Container health: API actuator and PostgreSQL health checks are healthy.
6. Browser scenario: an admin sees an assignment change and a non-admin cannot submit it.
7. Acceptance-criteria review, with one evidence item per criterion.

The orchestration state machine never treats an agent's completion message as proof. Every gate must have a timestamped command/result artifact. Repairs are capped per failed gate; manual intervention, skipped checks, and simulated provider results are represented explicitly in the final report.

## Current runner protocol

The local runner can report a required named container gate using a valid runner identity and active acknowledged lease:

```text
verify-container-gate-and-record <control-plane-url> <identity-file> <lease-file> <gate> <worktree-path> <timeout-seconds> <network:none|allow> <image> <command> [arguments...]
```

The control plane records the command evidence before deriving the gate result from the bounded container outcome. Exit code `0` without a timeout passes the named gate; every other result fails it. A failed gate blocks the owning run. A run reaches `READY_FOR_REVIEW` only after every required gate has passed. Gate names must already be present in the run policy; arbitrary runner-supplied names are rejected. This is a reporting primitive, not policy orchestration: the control plane does not yet select commands or automatically dispatch those gates.
