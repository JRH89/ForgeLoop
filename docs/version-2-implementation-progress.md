# ForgeLoop Version 2 implementation progress

Execution log for the seven reviewed designs in `docs/Version_2/`. Each implementation slice maps to an issue and a PR. Local tests, hosted CI, and provider-backed validation are tracked separately; no validation is inferred.

## Current status

- **Active work:** Slice 4a, fail-closed enforcement for agent-loop tool calls; issue [#83](https://github.com/JRH89/ForgeLoop/issues/83).
- **Local integration branch:** `feat/4a-enforcement-integration` combines PR #76 (2c), PR #80 (3b), and PR #82 (3c) solely to verify the dependency stack. Do not treat it as a delivery branch.
- **Commits:** `26b07ef` merges 2c/3b for local integration; `4707c10` implements runner-side 4a. Control-plane integration and enforcement reason handling are verified locally but not yet committed.
- **Open prerequisite PRs:** #72, #74, #76, #78, #80, and #82. User retains control of merges.
- **Safety boundary:** the agent loop remains dormant. Slice 4a must not enable or wire dispatch.
- **External validation:** no paid provider-backed run is included. Local HTTP fixtures and hosted CI are not provider-backed evidence.
- **Project direction:** `docs/original_outline.md`; approved design order is in `docs/Version_2/`.

## Planned issue-linked PR slices

| # | Slice | Depends on | Issue / PR | Status |
|---:|---|---|---|---|
| 1 | Sequenced writers | - | [#69](https://github.com/JRH89/ForgeLoop/issues/69) / [#70](https://github.com/JRH89/ForgeLoop/pull/70) | Merged; local and hosted verification passed |
| 2 | 2a - Test boundary | 1 | [#71](https://github.com/JRH89/ForgeLoop/issues/71) / [#72](https://github.com/JRH89/ForgeLoop/pull/72) | Open; local and hosted verification passed |
| 3 | 2b - RED/GREEN checks | 2a | [#73](https://github.com/JRH89/ForgeLoop/issues/73) / [#74](https://github.com/JRH89/ForgeLoop/pull/74) | Open, stacked on #72; hosted checks passed |
| 4 | 2c - GitHub branch check | 2b | [#75](https://github.com/JRH89/ForgeLoop/issues/75) / [#76](https://github.com/JRH89/ForgeLoop/pull/76) | Open, stacked on #74; hosted checks passed |
| 5 | 3a - Provider conversations and tool calling | 1 | [#77](https://github.com/JRH89/ForgeLoop/issues/77) / [#78](https://github.com/JRH89/ForgeLoop/pull/78) | Open; hosted checks passed |
| 6 | 3b - Agent loop core and journal | 3a | [#79](https://github.com/JRH89/ForgeLoop/issues/79) / [#80](https://github.com/JRH89/ForgeLoop/pull/80) | Open, stacked on #78; hosted checks passed; dormant |
| 7 | 3c - Control-plane loop policy and lease lifecycle | 3a; parallel with 3b | [#81](https://github.com/JRH89/ForgeLoop/issues/81) / [#82](https://github.com/JRH89/ForgeLoop/pull/82) | Open, stacked on #78; hosted checks passed |
| 8 | 3d - Runner loop wiring behind a default-off switch | 3b, 3c, 4a | Not opened | Planned; remain disabled until 4a is complete |
| 9 | 3e - Resume from the journal | 3b, 3d | Not opened | Planned / deferrable |
| 10 | 4a - Fail-closed security guard | 2a, 2b, 3b, 3c | [#83](https://github.com/JRH89/ForgeLoop/issues/83) / Not opened | Implemented and locally verified on integration stack; PR waits for prerequisite branches |
| 11 | 4b - RED prerequisite validation | 4a, 2b | Not opened | Planned |
| 12 | 4c - Repository enforcement policy | 4a | Not opened | Planned |
| 13 | 4d - Spend reservation and enforcement | 4a | Not opened | Planned / deferrable |
| 14 | 5a - Run-record identity and input pins | 3 | Not opened | Planned |
| 15 | 5b - Evidence links and record verification | 5a, 2 | Not opened | Planned |
| 16 | 5c - Exportable/verifiable record | 5a, 5b | Not opened | Planned |
| 17 | 5d-i - Trace capture | 3, 5a | Not opened | Planned |
| 18 | 5d-ii - Trace replay | 5d-i | Not opened | Planned |
| 19 | 5e - Drift probe | 5a, 5b | Not opened | Planned |
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
- **Delivery:** issue #83 is linked; a PR waits until prerequisite branches are merged so it can target a clean base.

## Update protocol

For each slice, record its linked issue/PR, meaningful commits, behavior, exact verification outcomes, and remaining external or paid validation. Mark complete only after all slice-local work is complete. Keep hosted CI and provider-backed evidence distinct. The product agent loop must stay dormant until the plan explicitly enables it.
