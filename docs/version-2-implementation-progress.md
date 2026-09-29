# ForgeLoop Version 2 implementation progress

Execution log for the seven reviewed designs in `docs/Version_2/`. Each implementation slice maps to an issue and a PR. Local tests, hosted CI, and provider-backed validation are tracked separately; no validation is inferred.

## Current status

- **Remaining work:** Slice 5e's offline implementation is merged in PR [#99](https://github.com/JRH89/ForgeLoop/pull/99), but issue [#90](https://github.com/JRH89/ForgeLoop/issues/90) remains open for canonical live-provider fixtures and optional paid drift validation. Slice 5d-i is complete in PR [#100](https://github.com/JRH89/ForgeLoop/pull/100).
- **Delivery branch:** PRs are prepared from the merged prerequisite chain and delivered one at a time.
- **Merged PR stack:** PRs #70, #72, #74, #76, #78, #80, #82, #92, #93, #94, #95, #96, #97, #98, #99, and #100 are merged to `master` (2026-09-29). Only one PR is open at a time.
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
| 12 | 4c - Repository enforcement policy | 4a | [#85](https://github.com/JRH89/ForgeLoop/issues/85) / [#94](https://github.com/JRH89/ForgeLoop/pull/94) | Merged to `master` (2026-09-29); hosted checks passed |
| 13 | 4d - Spend reservation and enforcement | 4a | [#86](https://github.com/JRH89/ForgeLoop/issues/86) / [#95](https://github.com/JRH89/ForgeLoop/pull/95) | Merged to `master` (2026-09-29); hosted checks passed |
| 14 | 5a - Run-record identity and input pins | 3 | [#87](https://github.com/JRH89/ForgeLoop/issues/87) / [#96](https://github.com/JRH89/ForgeLoop/pull/96) | Merged to `master`; issue closed; hosted checks passed |
| 15 | 5b - Attempt outcomes and attempt-local routing | 5a, 2 | [#88](https://github.com/JRH89/ForgeLoop/issues/88) / [#97](https://github.com/JRH89/ForgeLoop/pull/97) | Merged to `master`; issue closed; hosted checks passed |
| 16 | 5c - Opt-in record content and journal upload | 3, 5a, 5b | [#89](https://github.com/JRH89/ForgeLoop/issues/89) / [#98](https://github.com/JRH89/ForgeLoop/pull/98) | Merged to `master` (2026-09-29); hosted checks passed |
| 17 | 5e - Provider replay and drift probe | 3a, 5c fixtures | [#90](https://github.com/JRH89/ForgeLoop/issues/90) / [#99](https://github.com/JRH89/ForgeLoop/pull/99) | Offline implementation merged to `master`; canonical live fixtures and drift probe pending, so #90 remains open |
| 18 | 5d-i - Run-record export and core integrity checks | 5a, 5b, 5c, 5e | [#91](https://github.com/JRH89/ForgeLoop/issues/91) / [#100](https://github.com/JRH89/ForgeLoop/pull/100) | Merged to `master` (2026-09-29); issue closed; hosted checks passed |
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
- **Delivery:** merged as [PR #94](https://github.com/JRH89/ForgeLoop/pull/94) on 2026-09-29; issue #85 is closed. Full hosted checks passed, including end-to-end after an infrastructure-only retry.

## Slice 4d - Shared spend reservation

- **Issue:** [#86](https://github.com/JRH89/ForgeLoop/issues/86).
- **Result:** priced agent-loop turns now reserve a conservative worst-case cost with the control plane before the runner journals or sends the request. Run-scoped task locks serialize concurrent reservations; task and run known spend plus active sibling reservations must remain within budget. Reservations replace earlier values on retry, settle when provider usage is recorded, and are released when leases close or expire. Unknown pricing skips reservation; refusal stops before the provider call; control-plane transport failure retries with bounded backoff and then fails closed.
- **Verification:** the original full suites passed (control-plane 307 tests; runner 244 tests with 5 platform-dependent skips; harness 2 tests). Re-run on the #85 PR stack: control-plane 322 tests, runner 288 tests (9 platform/live-fixture skips), and harness 2 tests; all had 0 failures/errors. `git diff --check` passed. No paid provider-backed run; dispatch remains dormant.
- **Delivery:** merged as [PR #95](https://github.com/JRH89/ForgeLoop/pull/95) on 2026-09-29; issue #86 is closed. Hosted checks passed, including end-to-end and all four desktop package targets. No provider-backed calls.

## Slice 5a - Run-record identity and input pins

- **Issue:** [#87](https://github.com/JRH89/ForgeLoop/issues/87).
- **Result:** provider adapters now report the model that actually answered; acknowledged leases pin runner revision and JAR SHA-256; gate evidence records the checked commit, resolved platform image ID, output-truncation flag, and validated lease link without changing existing evidence bundle digests. Claims capture immutable execution/verification refs and dependency commit order, while code-ready and integration closes pin their result SHA. Run submission stores canonical, SHA-256-addressed JSON for repository, organization, harness, and enabled local MCP policy; only the digest is exposed in GraphQL.
- **Compatibility:** new GraphQL inputs are optional, the database migration is nullable with no backfill, and legacy/unpackaged runner builds are explicitly represented.
- **Verification:** original control-plane `mvn -B verify` passed (317 tests); runner `mvn -B -Dforgeloop.revision=deadbeef0 verify` passed (255 tests, 5 platform-dependent skips); harness `mvn -B verify` passed (2 tests). V42 applied successfully against an isolated PostgreSQL 18 instance; all 12 new columns and both lease indexes were present. Re-run on merged `master` plus 5a: control-plane 326 tests, runner 290 tests (9 platform/live-fixture skips), and harness 2 tests; all had 0 failures/errors. `git diff --check` passed. No provider-backed call was made.
- **Delivery:** merged as [PR #96](https://github.com/JRH89/ForgeLoop/pull/96) on 2026-09-29; issue #87 is closed. Hosted checks passed, including all four desktop package targets and end-to-end.

## Slice 5b - Attempt outcomes and attempt-local routing

- **Issue:** [#88](https://github.com/JRH89/ForgeLoop/issues/88).
- **Result:** expired lease rows are retained; every new close path records one of `CLEAN`, `FINDINGS`, `HARNESS_FAILURE`, or `STOPPED` and a bounded category. Verification and review repair routing now uses only evidence recorded under the closing lease, and general retry categories prefer the explicit runner category, then that lease's latest provider category, then the role-specific fallback. Test-check verdicts store their result meaning and role-prefixed reason.
- **Run meaning:** GraphQL exposes derived `FeatureRun.exitMeaning` and `exitReason`; successful, cancelled, held, failed, legacy, and unresolved-escalation cases are covered without persisting a duplicate run summary.
- **Migration:** V43 adds nullable `task_lease.outcome` and `outcome_category`; `claimed_at` already exists from V39. Existing closed rows remain unclassified rather than receiving invented outcomes.
- **Verification:** on the combined #87 + #88 stack, control-plane `mvn -q verify` passed (332 tests), runner `mvn -q -Dforgeloop.revision=deadbeef0 verify` passed (290 tests, 9 platform/live-fixture skips), and harness `mvn -q verify` passed (2 tests); zero failures or errors. V43 applied on a disposable PostgreSQL 18 instance and both columns were confirmed nullable. JPA-backed tests exercised the new batched outcome queries; `git diff --check` passed. No provider-backed calls or deployment changes.
- **Delivery:** merged as [PR #97](https://github.com/JRH89/ForgeLoop/pull/97) on 2026-09-29; issue #88 is closed. Hosted checks passed, including end-to-end and supply-chain checks.

## Slice 5c - Opt-in record content and journal upload

- **Issue:** [#89](https://github.com/JRH89/ForgeLoop/issues/89).
- **Result:** adds an administrator-only repository switch with revision/audit tracking and an immutable per-run policy snapshot. The dashboard explains the implications of storing prompts, provider responses, and repository context. The runner records attempt pins, context digests, exact request-body hashes, raw responses, patch refusals, and commit-object/file digests. Opted-in attempts upload bounded, whole-line gzip JSONL segments before lease close; uploaded copies redact recognizable tokens/private keys, preserve original-line hashes, and mark oversize records omitted. Uploads are retried once; failure reports metadata-only `RECORD_UPLOAD_FAILED` and does not block delivery. The control plane rejects malformed, out-of-lease, out-of-order, secret-bearing, oversized, or non-opted-in journals and restricts downloads to operators in the owning organization.
- **Verification:** on the combined #87–#89 stack, control-plane `mvn -q verify` passed (339 tests), runner `mvn -q -Dforgeloop.revision=deadbeef0 verify` passed (292 tests, 9 platform/live-fixture skips), and harness `mvn -q verify` passed (2 tests), all with zero failures/errors. Frontend `npm.cmd run check` passed: lint, 80 tests, TypeScript, production build, prerender of 19 public pages, and SEO checks. V44 applied on an isolated PostgreSQL 18 instance; all 45 migrations reached v44 and both switch columns were confirmed. `git diff --check` passed. No provider-backed calls or deployment changes.
- **Delivery:** merged as [PR #98](https://github.com/JRH89/ForgeLoop/pull/98) on 2026-09-29; issue #89 is closed. Hosted checks passed, including end-to-end and supply-chain checks.

## Slice 5e - Provider replay and drift probes

- **Issue:** [#90](https://github.com/JRH89/ForgeLoop/issues/90).
- **Implementation:** added bounded hash-chain-verified journal reading; single-call and conversation replay clients that reuse production serializers/parsers, enforce exact request matching and recorded failure order, and reject redacted/omitted records; fixture metadata/pin validation; parser path/type drift comparison; and a `provider-drift-check` CLI that refuses to call a provider without a matching live-recorded fixture. The optional drift call is capped at one 128-token health request and emits only path/type differences.
- **Offline worker coverage:** planner, guarded patch, review, and full agent-loop tests replay deterministic test-double responses with no credentials or network. These fixtures are synthetic test scaffolding, not canonical provider captures.
- **Verification:** runner `mvn -q verify` passed on the isolated delivery branch (292 tests, 0 failures/errors, 9 skipped: 5 platform-dependent and 4 live-fixture agreement tests awaiting real captures). A Windows CI check exposed a test-fixture CRLF conversion; the test clone now pins `core.autocrlf=false` and asserts byte-identical input. The corrected commit passed the complete hosted runner, end-to-end, all four package targets, CodeQL, and supply-chain checks. No provider call was made.
- **Pending external validation:** canonical live-observed provider fixtures and the live drift probe require an explicit provider key and budget. They have not been fabricated or run; issue #90 remains open until that acceptance evidence exists.
- **Delivery:** merged to `master` by [PR #99](https://github.com/JRH89/ForgeLoop/pull/99) on 2026-09-29. Issue #90 stays open for actual canonical live-observed fixture agreement and the optional drift probe; no live provider action was taken.

## Slice 5d-i - Run-record export and core integrity checks

- **Issue:** [#91](https://github.com/JRH89/ForgeLoop/issues/91).
- **Result:** adds an on-demand, tenant-checked, operator-only `GET /api/runs/{runId}/record` ZIP export with canonical `forgeloop.run-record/1` metadata, a SHA-256 sidecar, verified archived artifacts, and a digest-only export audit event. The runner adds keyless `verify-run` and `verify-journal` commands with PASS / FAIL / UNVERIFIABLE results and defined exit codes. Verification covers bounded safe archives, file and artifact digests, ordered journal segment metadata, hash-chain gaps and redaction, attempt/lease/task links, provider request and response evidence using production serializers/parsers, verifier-version skew, and Section 19 counters/consistency. The implementation stops at 5d-i: it does not reconstruct worktrees, replay integration, or execute gates.
- **Verification:** control-plane `mvn -q verify` passed (345 tests, 0 failures/errors); runner `mvn -q -Dforgeloop.revision=deadbeef0 verify` passed (308 tests, 0 failures/errors, 9 skipped: 5 platform-dependent and 4 live-fixture agreement checks); harness `mvn -q verify` passed (2 tests, 0 failures/errors). `git diff --check` passed. No paid provider call, live fixture capture, or deployment change.
- **Delivery:** merged as [PR #100](https://github.com/JRH89/ForgeLoop/pull/100) on 2026-09-29; issue #91 is closed. Hosted checks passed, including end-to-end, all four desktop package targets, CodeQL, and supply-chain checks.

## Update protocol

For each slice, record its linked issue/PR, meaningful commits, behavior, exact verification outcomes, and remaining external or paid validation. Mark complete only after all slice-local work is complete. Keep hosted CI and provider-backed evidence distinct. The product agent loop must stay dormant until the plan explicitly enables it.
