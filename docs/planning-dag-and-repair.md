# Planning, DAG scheduling, integration, and repair

Slice 5 converts an accepted feature specification into restart-safe, bounded work. This document describes the invariants enforced by the control plane and runner.

## Planner contract

The `PLANNER` role is non-writing. Its provider must return one JSON object with exactly `acceptanceCriteria` and `tasks`. Every task declares a stable run-local key, supported role, title, required runner capability, dependency keys, repository-relative owned path prefixes, an attempt budget from one through five, and a micro-dollar budget.

The runner validates the shape, path safety, keys, roles, dependencies, cycles, and total budget. The control plane independently repeats those checks in one transaction. A malformed, cyclic, over-budget, traversal-bearing, duplicate, or partially valid plan is rejected without exposing any planned task to dispatch. A planner lease closes only after criteria and the complete graph are persisted.

## Scheduling invariants

Only `PENDING` or `REPAIR_QUEUED` tasks are candidates. A runner receives a task only when:

- its capability exactly matches the task requirement;
- every dependency is in an allowed completed state;
- neither the task nor run has exhausted its known cost budget; and
- none of its owned paths overlaps an active task in the same run.

Claims lock all task rows for the run in a stable order before eligibility is checked again. This serializes competing claims and prevents two runners from racing into overlapping paths. Disjoint, dependency-ready backend and frontend tasks remain independently claimable and can run in parallel.

## Integration

An `INTEGRATION` task becomes eligible when each dependency is `CHANGE_READY`, `INTEGRATED`, or `VERIFIED`. Its runner receives only the declared dependency commit SHAs, creates a detached integration worktree from the policy base branch, and cherry-picks them in graph order without a shell. Success records the integration SHA and advances only those declared `CHANGE_READY` dependencies to `INTEGRATED`. A conflict aborts the cherry-pick and fails the lease into the repair policy.

## Bounded repair and exhaustion

A failed or expired lease increments only its owning task's attempt counter. If attempts remain, the task becomes `REPAIR_QUEUED`; dispatch presents the `REPAIR` role and a bounded context containing the failure category, change SHA, evidence digest, owned paths, and acceptance criteria. Raw provider prompts, source files, secrets, and unrestricted logs are not persisted in the repair package.

When the next failure would exceed the task's fixed attempt budget, the task becomes `FAILED` and the feature run becomes `BLOCKED`. A different task is never implicitly reset or repaired. Provider costs with known rates count against both task and run budgets; unknown costs remain explicitly unknown rather than fabricated.

## Operational checks

Use the normal build and runtime gates:

```powershell
docker compose build control-plane
docker build -t forgeloop-runner:local .\runner
docker compose up -d --wait
docker compose ps
```

For a migration proof, start the Compose project against an empty, disposable volume and confirm Flyway reaches schema version 16 before Hibernate validation succeeds. Never remove the normal ForgeLoop database volume for this check.
