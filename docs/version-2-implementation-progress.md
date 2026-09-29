# ForgeLoop Version 2 implementation progress

Execution log for the seven reviewed designs in `docs/Version_2/`. Each implementation slice gets its own issue-linked PR. This tracker records actual changes, commits, verification, and any validation not yet possible; no check or end-to-end result is inferred.

## Current status

- **Active work:** Piece 2a — test boundary.
- **Branch:** `feat/test-boundary`, rebasing the Slice 2a work onto `master` after prerequisite PR #70 merged.
- **Issue:** [#71 — Enforce test-first write boundaries per repository](https://github.com/JRH89/ForgeLoop/issues/71).
- **Code state:** Slice 2a is implemented and locally verified across control-plane, runner, and harness; the implementation commits are rebased directly onto merged `master` and ready for its issue-linked PR.
- **Slice 1 commits:** `9f6f21f` control-plane scheduling and validation; `f90d03b` runner chaining, context, and worktree setup; `e3fa9d2` role-select chained predecessors alongside server-added checks.
- **Slice 2a commits:** `8a367de` control-plane test-first policy and graph enforcement; `527d01f` runner write-boundary and JUnit report enforcement. Both are rebased directly onto merged `master`.
- **PR:** [#70 — Sequence dependent writer tasks](https://github.com/JRH89/ForgeLoop/pull/70), linked to #69. Merged into `master` on 2026-09-29; hosted checks passed.
- **Local test toolchain:** portable Temurin 21 and Maven 3.9.12 under the user-local ForgeLoop tools directory; no project files added for tooling.
- **External validation:** Docker Engine is available. A real provider-backed chained run has not been attempted because it incurs provider spend; do not treat unit/CI checks as that evidence.
- **Outline:** `docs/original_outline.md` is present and remains the project-level direction; the approved `docs/Version_2/` designs define this implementation sequence.

## Planned issue-linked PR slices

Future issue numbers and exact PR scope will be recorded when those dependencies are ready and checked against the current code.

| Order | Slice | Dependency | Issue / PR | Status |
|---:|---|---|---|---|
| 1 | 1 — Sequenced writers | — | [#69](https://github.com/JRH89/ForgeLoop/issues/69) / [#70](https://github.com/JRH89/ForgeLoop/pull/70) | Merged; local and hosted verification passed |
| 2 | 2a — Test boundary | 1 | [#71](https://github.com/JRH89/ForgeLoop/issues/71) / pending | Implementation, local verification, and code commits complete; opening PR |
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
- **Scope implemented:** both planner validators allow at most one writing dependency and reject multiple/non-writing dependencies; writer readiness accepts a predecessor at `CHANGE_READY`; execution base and affinity select the unique writing dependency by role, even when server-added non-writing dependencies (such as Slice 2a's RED check) are also present; missing predecessor commits fail closed; integration orders commits topologically while preserving peer order and placing quality repairs last; changed predecessor files are prioritized in context without widening patch permissions; prepared worktrees use the execution base ref.
- **Tests added:** valid three-writer chain and cycle rejection in both validators; predecessor readiness alongside a non-writing RED check, execution refs, repair/base behavior, integration ordering; dispatch affinity and authoritative claim rejection when a RED check is also present; planner prompt; real temporary Git chain and changed-file discovery; context prioritization/write-boundary preservation; prepared worktree base.
- **Test-first evidence:** focused tests were run against the pre-change logic and failed on the missing chained-writer behavior; after implementation, focused control-plane tests passed (18) and focused runner tests passed (21).
- **Focused final reruns:** control-plane `mvn -B -Dtest=TaskGraphValidatorTest,DeliveryTaskSequencingTest,RunnerDispatchServiceTest,TaskLeaseServiceTest test` — 19 tests, 0 failures/errors; runner `mvn -B -Dtest=PlannerPlanTest,PlannerWorkerTest,GitWorktreeManagerTest,RepositoryContextBuilderTest,GuardedPatchWorkerTest,RunnerMainTest test` — 22 tests, 0 failures/errors. These cover the added cycle cases.
- **Full verification:** `mvn -B verify` in `control-plane` — 228 tests, 0 failures/errors; in `runner` — 126 tests, 0 failures/errors; in `harness` — 2 tests, 0 failures/errors. Only tests/comments changed after the full runs; the final changed test sets then passed above.
- **Inter-slice compatibility check:** the 2a design adds a verified RED-check dependency to implementation writers. Slice 1 now explicitly filters writing dependencies by role in execution-base and affinity selection, with dispatch, claim, readiness, and base-ref tests covering the combined graph.
- **Latest local results:** control-plane `mvn -B verify` — 241 tests, 0 failures/errors; runner `mvn -B verify` — 138 tests, 0 failures/errors; harness `mvn -B verify` — 2 tests, 0 failures/errors. The control-plane suite includes GraphQL SDL construction and repository/run policy tests; runner tests pin the unchanged no-report Docker argv and the new JUnit mount.
- **Hosted checks:** all check runs on PR #70 passed; PR #70 merged into `master` on 2026-09-29. Slice 2a hosted checks will start after its branch is pushed.
- **Remaining:** complete the provider-backed end-to-end check when a provider budget is available. Provider-backed evidence remains unrun and is not implied by local or hosted tests.
- **Known limits kept in scope:** no cross-runner commit transfer or recovery of a vanished producer runner; these are out of this slice.

## Slice 2a log — Test boundary

- **Issue:** [#71 — Enforce test-first write boundaries per repository](https://github.com/JRH89/ForgeLoop/issues/71).
- **Depends on:** Slice 1 / PR #70, now merged into `master`.
- **Scope:** repository opt-in and run snapshot, report-enabled verification policy and external JUnit output mount, path-glob matcher in both Java modules, stored-role-derived patch write boundaries, test-first graph and prompt rules, and Docker capability requirements for test-first root writers.
- **Progress:** implemented: nullable `JUNIT_XML` policy snapshots and repository configuration mutation; enabled-repository/admin/gate/glob validation; immutable run snapshot; the two-module test-path matcher; stored-role-derived `ANY` / `TESTS_ONLY` / `NO_TESTS` task contract; atomic patch-boundary enforcement with a distinct failure category; conditional test-first planner contract and graph validation; Docker-capability dispatch/claim checks for root test writers and scaffolds; and an isolated report bind mount that leaves old verification argv unchanged when disabled.
- **Documentation:** expanded `docs/verification-policy-and-evidence.md` with the generic repository setup, the admin test-first mutation, glob semantics, and JUnit command guidance; removed the demo repository name from the example.
- **Verification:** after rebasing onto merged `master`, `mvn -B verify` passed in `control-plane` (241 tests), `runner` (138 tests), and `harness` (2 tests); `git diff --check` passed. No paid provider request or actual Docker policy execution was made.
- **Commits / PR:** `8a367de` control-plane and `527d01f` runner; the branch is based directly on `master`. PR targets `master` and links to #71.
- **Validation boundaries:** hosted CI starts after push. Full RED/GREEN evidence belongs to Slice 2b.

## Update protocol

For each slice, update the status table and its log with the issue/PR, meaningful commit(s), behavior changed, exact verification commands and results, and any remaining environmental or paid end-to-end validation. Only mark a slice complete when required local work is complete; keep hosted CI and provider-backed evidence distinct.
