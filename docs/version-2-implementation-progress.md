# ForgeLoop Version 2 implementation progress

Execution log for the seven reviewed designs in `docs/Version_2/`. Each implementation slice gets its own issue-linked PR. This tracker records actual changes, commits, verification, and any validation not yet possible; no check or end-to-end result is inferred.

## Current status

- **Active work:** Slice 3c - control-plane agent-loop policy and lease lifecycle; can proceed alongside slice 3b.
- **Branch:** `feat/agent-loop-core`, stacked on `feat/provider-conversations` / PR #78.
- **Issue / PR:** [#79 - Implement the runner agent loop, tools, and durable step journal](https://github.com/JRH89/ForgeLoop/issues/79) / [#80](https://github.com/JRH89/ForgeLoop/pull/80).
- **Code state:** Slice 3b is committed and pushed in commits `fa1205c` (implementation) and `467df94` (docs), with PR #80 stacked on #78. Full runner verification passed (180 tests, zero failures/errors/skips). The loop is not wired into production dispatch. PR #80 hosted checks are pending.
- **Previous slice:** 3a is implemented at code commit `0f4a2b4`; docs progress commit `04ae22b`; PR #78 remains open with all hosted checks passing.
- **Earlier slices:** PR #70 is merged. PR #72 (2a), #74 (2b), and #76 (2c) are open; #74 and #76 remain stacked while PR #72 awaits merge. All hosted checks on #76 passed.
- **Local test toolchain:** portable Temurin 21 and Maven 3.9.12 under the user-local ForgeLoop tools directory; no project files added for tooling.
- **External validation:** live provider-backed runs have not been attempted because they incur provider spend. Local HTTP-server fixtures and hosted CI are not paid-provider evidence.
- **Outline:** `docs/original_outline.md` is the project-level direction; the approved `docs/Version_2/` designs define this implementation sequence.

## Planned issue-linked PR slices

Future issue numbers and exact PR scope will be recorded when those dependencies are ready and checked against the current code.

| Order | Slice | Dependency | Issue / PR | Status |
|---:|---|---|---|---|
| 1 | 1 - Sequenced writers | - | [#69](https://github.com/JRH89/ForgeLoop/issues/69) / [#70](https://github.com/JRH89/ForgeLoop/pull/70) | Merged; local and hosted verification passed |
| 2 | 2a - Test boundary | 1 | [#71](https://github.com/JRH89/ForgeLoop/issues/71) / [#72](https://github.com/JRH89/ForgeLoop/pull/72) | Local and hosted verification passed; PR open |
| 3 | 2b - RED/GREEN checks | 2a | [#73](https://github.com/JRH89/ForgeLoop/issues/73) / [#74](https://github.com/JRH89/ForgeLoop/pull/74) | Hosted checks passed; open and stacked on PR #72 |
| 4 | 2c - GitHub branch check | 2b | [#75](https://github.com/JRH89/ForgeLoop/issues/75) / [#76](https://github.com/JRH89/ForgeLoop/pull/76) | Hosted checks passed; open and stacked on PR #74 |
| 5 | 3a - Provider conversations and tool calling | 1 | [#77](https://github.com/JRH89/ForgeLoop/issues/77) / [#78](https://github.com/JRH89/ForgeLoop/pull/78) | Open against `master`; all hosted checks passing |
| 6 | 3b - Agent loop core and journal write path | 3a | [#79](https://github.com/JRH89/ForgeLoop/issues/79) / [#80](https://github.com/JRH89/ForgeLoop/pull/80) | Implementation/docs committed and pushed; local runner verification passed; hosted checks pending; stacked on PR #78 |
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
- **Latest focused result:** control-plane affinity/readiness/base-ref subset — 13 tests, 0 failures/errors. PR #70 was subsequently merged into `master` on 2026-09-29 after all hosted checks passed.
- **Remaining:** complete the provider-backed end-to-end check when a provider budget is available. Provider-backed evidence remains unrun and is not implied by CI.
- **Known limits kept in scope:** no cross-runner commit transfer or recovery of a vanished producer runner; these are out of this slice.

## Slice 2a log - Test boundary

- **Issue / PR:** [#71](https://github.com/JRH89/ForgeLoop/issues/71) / [#72](https://github.com/JRH89/ForgeLoop/pull/72), based on merged PR #70.
- **Scope:** repository test-first opt-in and run snapshot, JUnit report mount, path globs, stored-role write boundaries, test-first graph/prompt rules, and Docker capability requirements.
- **Implemented:** repository configuration and validation; immutable run policy; test-path matching; `ANY` / `TESTS_ONLY` / `NO_TESTS` task boundary; atomic patch enforcement; conditional planner contract and graph validation; runner dispatch/claim checks; report-enabled container mount without changing existing argv when disabled.
- **Docs and verification:** `docs/verification-policy-and-evidence.md` describes generic setup, admin mutation, glob semantics, and JUnit reporting. `mvn -B verify` passed in control-plane (241 tests), runner (138), and harness (2); hosted checks passed. No paid provider request was made.
- **Commits:** `8a367de` control plane; `527d01f` runner.

## Slice 2b log - RED/GREEN checks

- **Issue / PR:** [#73](https://github.com/JRH89/ForgeLoop/issues/73) / [#74](https://github.com/JRH89/ForgeLoop/pull/74), stacked on PR #72 because #72 remains open.
- **Scope and implementation:** bounded secure JUnit parsing; server-created RED/GREEN tasks and immutable evidence; deterministic verdicts, retries, idempotency and routing; runner affinity/leases; test-writer retries and quality repairs; `UNVERIFIABLE` hold/escalation; report upload/query; repair/review context; integration dependencies preventing unchecked test commits from becoming the head.
- **Verification:** `mvn -B verify` passed in control-plane (260 tests), runner (153 tests, 1 symlink-permission skip), and harness (2). The runner included the Docker report-mount test. Hosted checks passed at `a45569f` after migration V38 and a Windows-container test guard. No paid provider run was attempted.
- **Commits:** `0872763` control-plane; `913e6a5` runner; `1cdd879` tracker.

## Slice 2c log - GitHub branch check

- **Issue / PR:** [#75](https://github.com/JRH89/ForgeLoop/issues/75) / [#76](https://github.com/JRH89/ForgeLoop/pull/76), stacked on PR #74.
- **Scope and implementation:** authenticated GitHub compare-files request; verify published test blobs against current passing RED evidence; fail closed on removals, stale/incomplete/conflicting evidence, invalid globs, and the 300-file cap; persist held integration, blocked run, HIGH escalation, audit event, and remote head on violation.
- **Verification:** control-plane `mvn -B verify` passed (271 tests); focused compare/verifier/push/lease/evidence tests passed (27); runner `mvn -B verify` passed (153 tests, 1 symlink-permission skip); `git diff --check` passed. All hosted checks on PR #76 passed. PR #76 remains open and stacked on #74.
- **Commit:** `9447671`. No paid provider call was needed.

## Slice 3a log - Provider conversations and tool calling

- **Issue / PR:** [#77](https://github.com/JRH89/ForgeLoop/issues/77) / [#78](https://github.com/JRH89/ForgeLoop/pull/78), based directly on `master` and dependent on merged writer sequencing (PR #70), not on PR #76.
- **Scope:** add the normalized immutable conversation contract beside existing single-call execution; vendor-native tool-call/replay serialization and parsing; bounded retry, cost, and redacted usage behavior; keep the agent loop disabled.
- **Implemented:** request/item/turn types; Anthropic Messages, OpenAI Responses, OpenAI-compatible Chat Completions, and Gemini adapters; vendor-native replay and tool-result ordering; normalized stop reasons and usage; retryable malformed argument handling; optional `toolCalling` policy with hosted-vendor defaults and legacy policy compatibility; turn-level cost/usage evidence; and `provider-tool-check <provider> <model>`, which prints only stop reasons, token counts, and attempt counts.
- **Verification:** runner `mvn -B verify` — 150 tests, 0 failures/errors/skips. Local HTTP-server tests verify all adapters send exactly the UTF-8 bytes returned by `serialize`; fixtures cover signatures/reasoning replay, tool-only responses, result ordering, malformed arguments, stop reasons, usage, policy defaults, and retries. `git diff --check` passed.
- **Commit:** `0f4a2b4` — conversation API, four adapters, policy, diagnostic command, and tests.
- **Hosted checks:** pending on PR #78 (2026-09-28).
- **Paid validation boundary:** the diagnostic calls the selected provider twice and may incur charges. It was not run. No model loop or task execution was enabled.

## Slice 3b log - Agent loop core and journal write path

- **Issue / PR:** [#79](https://github.com/JRH89/ForgeLoop/issues/79) / [#80](https://github.com/JRH89/ForgeLoop/pull/80); branch `feat/agent-loop-core`, stacked on PR #78 (`feat/provider-conversations`).
- **Scope:** provider-neutral loop, role-derived grants, guarded list/read/search/write/edit tools, advisory gate execution seam, single tool gateway with interceptor hooks and one transient retry, context/result shaping, call/token/wall/context/money/output budgets, finish validation and commit, and runner-local write-ahead journal.
- **Implementation:** added `AgentLoop`, immutable loop contracts, fixed loop instructions and paths-only context manifest; centralized `.git`, symlink, worktree-root and owned-prefix checks; bounded regex/file traversal and atomic writes; added durable sequenced/hash-linked JSONL records, owner-only POSIX permissions, torn-tail recovery, and complete loop/request/response/tool/post-image records. Journal start records now pin repository identity, provider/model/attempt limit, adapter and serializer versions, exact instructions, tool specifications, gates, budget and base SHA. Exact model-visible tool results and metadata-only `LOOP_TOOL_CALLED` events are recorded for later replay and progress reporting.
- **Dormant boundary:** no dispatch path invokes this loop. Control-plane policy and lease renewal are 3c; runner wiring and Docker gate execution are 3d; journal resume is 3e. No hosted-provider calls were made and no cost was incurred.
- **Verification:** runner `mvn -B verify` passed after the implementation and review hardening: 180 tests, 0 failures, 0 errors, 0 skipped. `git diff --check` passed. Hosted checks for dependency PR #78 are all green.
- **Delivery:** commits `fa1205c` and `467df94` pushed; PR #80 targets `feat/provider-conversations`, depends on PR #78, and closes issue #79. Do not merge until the stack's base is merged and checks are green.

## Update protocol

For each slice, update the status table and its log with the issue/PR, meaningful commit(s), behavior changed, exact verification commands and results, and any remaining environmental or paid end-to-end validation. Only mark a slice complete when required local work is complete; keep hosted CI and provider-backed evidence distinct.
