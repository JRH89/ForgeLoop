# ForgeLoop Version 2 implementation progress

Execution log for the seven reviewed designs in `docs/Version_2/`. Each implementation slice maps to an issue and a PR. Local tests, hosted CI, and provider-backed validation are tracked separately; no validation is inferred.

## Current status

- **Active work:** Slice 4c, repository-scoped agent-loop enforcement policy; issue [#85](https://github.com/JRH89/ForgeLoop/issues/85).
- **Delivery branch:** `feat/4c-repository-enforcement-pr` contains only the 4c slice plus this tracker update, based directly on the merged prerequisite chain.
- **Merged PR stack:** PRs #70, #72, #74, #76, #78, #80, #82, #92, and #93 are merged to `master` (2026-09-29). Only one PR is opened at a time.
- **Issue provenance:** #83-#86 decompose design 04 into slices 4a-4d; #87-#90 track design 05 slices 5a, 5b, 5c, and 5e; #91 tracks 5d-i. Slice 5d-ii is still planned without an issue.
- **Safety boundary:** the agent loop remains dormant. Slices 4a–4c add enforcement contracts only; they must not enable or wire dispatch.
- **External validation:** no paid provider-backed run is included. Local HTTP fixtures and hosted CI are not provider-backed evidence.
- **Project direction:** `docs/original_outline.md`; approved design order is in `docs/Version_2/`.

## Planned issue-linked PR slices

| # | Slice | Depends on | Issue / PR | Status |
|---:|---|---|---|---|
| 1 | Sequenced writers | - | [#69](https://github.com/JRH89/ForgeLoop/issues/69) / [#70](https://github.com/JRH89/ForgeLoop/pull/70) | Merged; local and hosted verification passed |
| 2 | 2a - Test boundary | 1 | [#71](https://github.com/JRH89/ForgeLoop/issues/71) / [#72](https://github.com/JRH89/ForgeLoop/pull/72) | Merged to `master` (2026-09-29) |
| 3 | 2b - RED/GREEN checks | 2a | [#73](https://github.com/JRH89/ForgeLoop/issues/73) / [#74](https://github.com/JRH89/ForgeLoop/pull/74) | Merged to `master` (2026-09-29) |
| 4 | 2c - GitHub branch check | 2b | [#75](https://github.com/JRH89/ForgeLoop/issues/75) / [#76](https://github.com/JRH89/ForgeLoop/pull/76) | Merged to `master` (2026-09-29) |
| 5 | 3a - Provider conversations and tool calling | 1 | [#77](https://github.com/JRH89/ForgeLoop/issues/77) / [#78](https://github.com/JRH89/ForgeLoop/pull/78) | Merged to `master` (2026-09-29) |
| 6 | 3b - Agent loop core and journal | 3a | [#79](https://github.com/JRH89/ForgeLoop/issues/79) / [#80](https://github.com/JRH89/ForgeLoop/pull/80) | Merged to `master` (2026-09-29); remains dormant |
| 7 | 3c - Control-plane loop policy and lease lifecycle | 3a; parallel with 3b | [#81](https://github.com/JRH89/ForgeLoop/issues/81) / [#82](https://github.com/JRH89/ForgeLoop/pull/82) | Merged to `master` (2026-09-29); execution remains dormant |
| 8 | 3d - Runner loop wiring behind a default-off switch | 3b, 3c, 4a | Not opened | Planned; remain disabled until 4a is complete |
| 9 | 3e - Resume from the journal | 3b, 3d | Not opened | Planned / deferrable |
| 10 | 4a - Fail-closed security guard | 2a, 2b, 3b, 3c | [#83](https://github.com/JRH89/ForgeLoop/issues/83) / [#92](https://github.com/JRH89/ForgeLoop/pull/92) | Merged to `master` (2026-09-29); dispatch remains dormant |
| 11 | 4b - RED prerequisite validation | 4a, 2b | [#84](https://github.com/JRH89/ForgeLoop/issues/84) / [#93](https://github.com/JRH89/ForgeLoop/pull/93) | Merged to `master` (2026-09-29) |
| 12 | 4c - Repository enforcement policy | 4a | [#85](https://github.com/JRH89/ForgeLoop/issues/85) / PR pending | Implementation isolated; full local verification passed; hosted checks pending |
| 13 | 4d - Spend reservation and enforcement | 4a | [#86](https://github.com/JRH89/ForgeLoop/issues/86) | Implemented locally; queued behind 4c |
| 14 | 5a - Run-record identity and input pins | 3 | [#87](https://github.com/JRH89/ForgeLoop/issues/87) | Implemented locally; queued behind 4d |
| 15 | 5b - Attempt outcomes and attempt-local routing | 5a, 2 | [#88](https://github.com/JRH89/ForgeLoop/issues/88) | Implemented locally; queued behind 5a |
| 16 | 5c - Opt-in record content and journal upload | 3, 5a, 5b | [#89](https://github.com/JRH89/ForgeLoop/issues/89) | Implemented locally; queued behind 5b |
| 17 | 5e - Provider replay and drift probe | 3a, 5c fixtures | [#90](https://github.com/JRH89/ForgeLoop/issues/90) | Offline replay verified; live canonical fixtures and paid drift calls pending |
| 18 | 5d-i - Run-record export and core integrity checks | 5a, 5b, 5c, 5e | [#91](https://github.com/JRH89/ForgeLoop/issues/91) | Planned after prerequisite slices; no paid provider calls or live fixture capture |
| 19 | 5d-ii - Deterministic re-execution checks | 5d-i | Not opened | Planned |
| 20 | 6a - Pull-request rounds and lifecycle | 2-5 | Not opened | Planned |
| 21 | 6b-i - GitHub feedback sweep and evidence | 6a | Not opened | Planned |
| 22 | 6b-ii - Review comments and permission checks | 6b-i | Not opened | Planned |
| 23 | 6c - Test-changing review rounds | 2b, 6b | Not opened | Planned |
| 24 | 7a-i - Intake core, claims, caps, and decision ledger | 1-6 (`UntrustedText`) | Not opened | Planned |
| 25 | 7a-ii - GitHub issue intake source | 7a-i | Not opened | Planned |
| 26 | 7b - Human approval to start a run | 7a-i, 2a, 2b | Not opened | Planned |
| 27 | 7c - Two-pass triage and sampled audit | 7b, 5b | Not opened | Planned |

## Completed implementation logs

### Slice 1 - Sequenced writers

- **Issue / PR:** [#69](https://github.com/JRH89/ForgeLoop/issues/69) / [#70](https://github.com/JRH89/ForgeLoop/pull/70), merged.
- **Result:** single-writer dependency chains, cycle/missing-commit refusal, topological integration, predecessor context prioritization without wider write permissions, and execution-base preparation.
- **Verification:** control-plane, runner, and harness full suites passed (228, 126, and 2 tests). Provider-backed end-to-end validation remains unrun to avoid spend.

### Slice 2a - Test boundary

- **Issue / PR:** [#71](https://github.com/JRH89/ForgeLoop/issues/71) / [#72](https://github.com/JRH89/ForgeLoop/pull/72).
- **Result:** repository test-first snapshot, JUnit report mount, role-derived write boundaries, atomic patch enforcement, planner rules, and Docker capability checks.
- **Verification:** control-plane, runner, and harness full suites passed (241, 138, and 2 tests); hosted checks passed.

### Slice 2b - RED/GREEN checks

- **Issue / PR:** [#73](https://github.com/JRH89/ForgeLoop/issues/73) / [#74](https://github.com/JRH89/ForgeLoop/pull/74), stacked on #72.
- **Result:** bounded JUnit parsing; immutable RED/GREEN evidence; deterministic verdicts, retries, idempotency, runner affinity, quality repair, unverifiable holds, and integration dependencies.
- **Verification:** full suites passed (control-plane 260, runner 153 with one symlink-permission skip, harness 2); hosted checks passed. No paid provider run.

### Slice 2c - GitHub branch check

- **Issue / PR:** [#75](https://github.com/JRH89/ForgeLoop/issues/75) / [#76](https://github.com/JRH89/ForgeLoop/pull/76), stacked on #74.
- **Result:** authenticated compare-files validation against current passing RED evidence; fail-closed removal/staleness/conflict/invalid-glob/file-cap handling; held integration and HIGH escalation.
- **Verification:** control-plane full suite passed (271 tests); runner full suite passed (153 tests, one symlink-permission skip); hosted checks passed.

### Slice 3a - Provider conversations and tool calling

- **Issue / PR:** [#77](https://github.com/JRH89/ForgeLoop/issues/77) / [#78](https://github.com/JRH89/ForgeLoop/pull/78).
- **Result:** immutable conversation contract, Anthropic/OpenAI/Gemini adapters, native tool replay, normalized usage, bounded malformed-argument retry, optional tool-calling policy, and provider diagnostic.
- **Verification:** runner full suite passed (150 tests); local HTTP fixtures pin provider wire serialization; hosted checks passed. Diagnostic was not run because it makes paid calls.

### Slice 3b - Agent loop core and journal

- **Issue / PR:** [#79](https://github.com/JRH89/ForgeLoop/issues/79) / [#80](https://github.com/JRH89/ForgeLoop/pull/80), stacked on #78.
- **Result:** dormant provider-neutral loop; role-derived grants; bounded tools and context; budget/finish checks; hash-linked JSONL journal with secure permissions and torn-tail recovery.
- **Verification:** runner full suite passed (180 tests); hosted checks passed. Dispatch is not wired and no hosted provider call was made.

### Slice 3c - Control-plane loop policy and lease lifecycle

- **Issue / PR:** [#81](https://github.com/JRH89/ForgeLoop/issues/81) / [#82](https://github.com/JRH89/ForgeLoop/pull/82), stacked on #78 and independent of #80.
- **Result:** validated repository loop budgets/audit, immutable run/task snapshots, bounded lease renewal, loop holds/escalations, optional failure category, and metadata-only events. Its migration is V36 on PR #82's base; on the combined test-first stack it is V39 because V36-V38 are already used.
- **Verification:** PR #82's branch passed its 244-test control-plane suite and hosted checks. The combined control-plane suite passes locally with all predecessor-stack behavior retained (288 tests). Dispatch remains unchanged and no provider-backed call was made.

## Slice 4a - Fail-closed security guard

- **Issue:** [#83](https://github.com/JRH89/ForgeLoop/issues/83).
- **Runner commit:** `4707c10`.
- **Result:** fixed descriptor-derived interceptor chain; preflight before any provider request; credential/protected-path/write-boundary/secret-content rules; result redaction; fail-closed before/after hooks; `HOLD` and `POLICY_HOLD`; descriptor/decision journal evidence. Dispatch remains dormant.
- **Documentation:** [Agent-loop enforcement](agent-loop-enforcement.md).
- **Runner verification:** focused suite passed (32 tests), then `mvn -B verify` passed (222 tests, 0 failures/errors, 1 existing symlink-permission skip).
- **Control-plane:** `hold` accepts the four `ENFORCEMENT_<CLASS>` reasons; all four produce HIGH escalations. Unit tests cover acceptance, state transition, and severity.
- **Verification:** runner `mvn -B verify` passed (222 tests, 0 failures/errors, 1 existing symlink-permission skip); combined control-plane `mvn -B verify` passed (288 tests, 0 failures/errors/skips); harness `mvn -B verify` passed (2 tests). `git diff --check` passed. No paid provider run.
- **Delivery:** merged as [PR #92](https://github.com/JRH89/ForgeLoop/pull/92) on 2026-09-29; issue #83 is closed. Hosted control-plane, runner, harness, frontend, MCP, end-to-end, installer, desktop-package, supply-chain, and CodeQL checks passed. No paid provider run.

## Slice 4b - RED prerequisite validation

- **Issue:** [#84](https://github.com/JRH89/ForgeLoop/issues/84).
- **Scope:** derive the RED proof from the implementation task's `RED_CHECK` edge and the check's test writer; dispatch the test task ID, target SHA, and evidence digest; reject missing, malformed, stale, or mismatched proof before the first provider turn.
- **Result:** control-plane derives the current passing record from the exact RED-check/test-writer edges without calling the base-ref helper; GraphQL and runner transport preserve the test ID, target SHA, and evidence digest; absent, malformed, stale, and mismatched proof holds before a provider turn. Dispatch caches the materialized value before lazy persistence state leaves the transaction.
- **Verification:** focused runner suite passed (23 tests); focused control-plane suite passed (11 tests); full runner `mvn -B verify` passed (226 tests, 1 existing symlink-permission skip); full control-plane `mvn -B verify` passed (294 tests); harness `mvn -B verify` passed (2 tests). No paid provider run.
- **Delivery:** merged as [PR #93](https://github.com/JRH89/ForgeLoop/pull/93) on 2026-09-29; issue #84 is closed. Hosted checks passed; no paid provider run.

## Slice 4c - Repository enforcement policy

- **Issue:** [#85](https://github.com/JRH89/ForgeLoop/issues/85).
- **Result:** administrator-only repository enforcement settings validate and audit custom protected globs, the default-deny workflow opt-out, and an optional repository verification finish gate. Settings are persisted on repositories and snapshotted immutably on feature runs, then exposed to writing tasks through GraphQL. The runner fingerprints and validates the policy before any provider request, enforces custom paths and opt-out semantics, and redirects finish until a qualifying post-write gate run exists. The loop remains dormant.
- **Additional safety fix:** safe dot-prefixed paths such as `.github/workflows/ci.yml` are accepted by structured patch validation; empty, dot, parent/traversal segments remain rejected. This makes the audited workflow opt-out usable without widening repository confinement.
- **Verification:** focused control-plane (18 tests) and runner (37 tests) suites passed. Re-run against merged `master`: control-plane `mvn -B verify` passed (316 tests), runner `mvn -B verify` passed (286 tests, 9 existing platform/live-fixture skips), and harness `mvn -B verify` passed (2 tests). `git diff --check` passed. No provider-backed work was run.
- **Delivery:** implementation and documentation are isolated on `feat/4c-repository-enforcement-pr`; issue #85 is linked. Full local verification passed against the merged prerequisite chain; hosted checks will run on its linked PR.

## Update protocol

For each slice, record its linked issue/PR, meaningful commits, behavior, exact verification outcomes, and remaining external or paid validation. Mark complete only after all slice-local work is complete. Keep hosted CI and provider-backed evidence distinct. The product agent loop must stay dormant until the plan explicitly enables it.
