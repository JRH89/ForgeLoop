# ForgeLoop Version 2 implementation progress

Execution log for the seven reviewed designs in `docs/Version_2/`. Each implementation slice gets its own issue-linked PR. This tracker records actual changes, commits, verification, and any validation not yet possible; no check or end-to-end result is inferred.

## Current status

- **Active work:** Piece 1 — sequenced writers.
- **Branch:** `feat/sequenced-writers`, based on `origin/master` after merged PR #68 (`0fb7a9c`).
- **Issue:** [#69 — Allow writers to build on a predecessor task commit](https://github.com/JRH89/ForgeLoop/issues/69).
- **Code state:** implemented, locally verified, and pushed; GitHub Actions checks are running.
- **Commits:** `9f6f21f` control-plane scheduling and validation; `f90d03b` runner chaining, context, and worktree setup.
- **PR:** [#70 — Sequence dependent writer tasks](https://github.com/JRH89/ForgeLoop/pull/70), linked to #69. No merge has been performed.
- **Local test toolchain:** portable Temurin 21 and Maven 3.9.12 under the user-local ForgeLoop tools directory; no project files added for tooling.
- **External validation:** Docker Engine is available. A real provider-backed chained run has not been attempted because it incurs provider spend; do not treat unit/CI checks as that evidence.

## Planned issue-linked PR slices

Future issue numbers and exact PR scope will be recorded when those dependencies are ready and checked against the current code.

| Order | Slice | Dependency | Issue / PR | Status |
|---:|---|---|---|---|
| 1 | 1 — Sequenced writers | — | [#69](https://github.com/JRH89/ForgeLoop/issues/69) / [#70](https://github.com/JRH89/ForgeLoop/pull/70) | Local verification passed; hosted CI running |
| 2 | 2a — Test boundary | 1 | Not opened | Planned |
| 3 | 2b — RED/GREEN checks | 2a | Not opened | Planned |
| 4 | 2c — GitHub branch check | 2b | Not opened | Planned |
| 5 | 3a — Provider conversations and tool calling | 1 | Not opened | Planned |
| 6 | 3b — Agent loop core and journal write path | 3a | Not opened | Planned |
| 7 | 3c — Control-plane loop policy and lease lifecycle | 3a; can proceed alongside 3b | Not opened | Planned |
| 8 | 3d — Runner loop wiring behind a default-off switch | 3b, 3c, 4a; keep disabled until 4a | Not opened | Planned |
| 9 | 3e — Resume from the journal | 3b, 3d | Not opened | Planned / deferrable |
| 10 | 4a — Fail-closed security guard | 1, 2, 3 | Not opened | Planned |
| 11 | 4b — Tool permission enforcement | 4a | Not opened | Planned |
| 12 | 4c — Isolated command execution | 4a, 4b | Not opened | Planned |
| 13 | 4d — Spend reservation and enforcement | 4a | Not opened | Planned |
| 14 | 5a — Run-record identity and input pins | 3 | Not opened | Planned |
| 15 | 5b — Evidence links and record verification | 5a, 2 | Not opened | Planned |
| 16 | 5c — Exportable/verifiable record | 5a, 5b | Not opened | Planned |
| 17 | 5d-i — Trace capture | 3, 5a | Not opened | Planned |
| 18 | 5d-ii — Trace replay | 5d-i | Not opened | Planned |
| 19 | 5e — Drift probe | 5a, 5b | Not opened | Planned |
| 20 | 6a — Pull-request rounds and lifecycle | 2–5 | Not opened | Planned |
| 21 | 6b-i — GitHub feedback sweep and evidence | 6a | Not opened | Planned |
| 22 | 6b-ii — Review comments and permission checks | 6b-i | Not opened | Planned |
| 23 | 6c — Test-changing review rounds | 2b, 6b | Not opened | Planned |
| 24 | 7a-i — Intake core, claims, caps, and decision ledger | 1–6 (`UntrustedText`) | Not opened | Planned |
| 25 | 7a-ii — GitHub issue intake source | 7a-i | Not opened | Planned |
| 26 | 7b — Human approval to start a run | 7a-i, 2a, 2b | Not opened | Planned |
| 27 | 7c — Two-pass triage and sampled audit | 7b, 5b | Not opened | Planned |

## Slice 1 log — Sequenced writers

- **Issue:** [#69](https://github.com/JRH89/ForgeLoop/issues/69).
- **PR:** [#70 — Sequence dependent writer tasks](https://github.com/JRH89/ForgeLoop/pull/70).
- **Scope implemented:** both planner validators allow at most one writing dependency and reject multiple/non-writing dependencies; writer readiness accepts a predecessor at `CHANGE_READY`; execution starts at the predecessor commit and fails closed if it is missing; chained writers are offered to and claimable only by the runner holding that commit; integration orders commits topologically while preserving peer order and placing quality repairs last; changed predecessor files are prioritized in context without widening patch permissions; prepared worktrees use the execution base ref.
- **Tests added:** valid three-writer chain and cycle rejection in both validators; predecessor readiness, execution refs, repair/base behavior, integration ordering; dispatch affinity and authoritative claim rejection; planner prompt; real temporary Git chain and changed-file discovery; context prioritization/write-boundary preservation; prepared worktree base.
- **Test-first evidence:** focused tests were run against the pre-change logic and failed on the missing chained-writer behavior; after implementation, focused control-plane tests passed (18) and focused runner tests passed (21).
- **Focused final reruns:** control-plane `mvn -B -Dtest=TaskGraphValidatorTest,DeliveryTaskSequencingTest,RunnerDispatchServiceTest,TaskLeaseServiceTest test` — 19 tests, 0 failures/errors; runner `mvn -B -Dtest=PlannerPlanTest,PlannerWorkerTest,GitWorktreeManagerTest,RepositoryContextBuilderTest,GuardedPatchWorkerTest,RunnerMainTest test` — 22 tests, 0 failures/errors. These cover the added cycle cases.
- **Full verification:** `mvn -B verify` in `control-plane` — 228 tests, 0 failures/errors; in `runner` — 126 tests, 0 failures/errors; in `harness` — 2 tests, 0 failures/errors. Only tests/comments changed after the full runs; the final changed test sets then passed above.
- **Remaining:** record hosted CI results and complete the provider-backed end-to-end check when a provider budget is available. Provider-backed evidence remains unrun and is not implied by CI.
- **Known limits kept in scope:** no cross-runner commit transfer or recovery of a vanished producer runner; these are out of this slice.

## Update protocol

For each slice, update the status table and its log with the issue/PR, meaningful commit(s), behavior changed, exact verification commands and results, and any remaining environmental or paid end-to-end validation. Only mark a slice complete when required local work is complete; keep hosted CI and provider-backed evidence distinct.
