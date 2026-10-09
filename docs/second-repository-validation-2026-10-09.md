# Second-repository live validation — 2026-10-09

Status: BLOCKED; the end-to-end test did not pass.

- Target: `JRH89/project-hub`, issue #6 (trim whitespace in file searches).
- Run: `440074fe-e140-4c56-a5db-2209b69e4457`.
- Organization and repository maximum budget: USD 5; auto-merge enabled.
- Removed the intake label from old issue #5 to isolate the test.
- Initial intake failed because the new server had no repository verification policy.
- Configured only project-hub through an explicit server-side database maintenance
  transaction, not an authenticated API mutation. Guarded the transaction against
  incorrect budget, repository, organization policy, and test-first configuration.
  Inserted the required policy and advanced the repository policy revision. This
  maintenance operation does not create an application audit-ledger event.
- Required `unit` gate: Node 22 bookworm-slim pinned to
  `sha256:efd0ab5780c2d9ab1f0f869571a00d5edb17793bff4cce4a2792e3eb0ffc7562`;
  EGRESS networking; 900-second timeout; ALL criterion coverage.
- Command: `ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm ci --no-audit --no-fund && node --test tests/search.test.js && npm run build`.
- Reapplied the issue intake label; confirmed the run entered PLANNING.

No run state, approval, verification evidence, or merge result has been manually
modified. The dashboard lacks an editable verification-policy form; the heading
on run details displays results only. That onboarding gap remains follow-up work.

## Observed outcome

- Planning, implementation, and integration completed automatically.
- The review provider succeeded on its second request, but rejected the change.
  Its summary incorrectly interpreted `Search.search` as returning Fuse result
  wrappers; the inspected implementation returns `results.map(result => result.item)`.
- Repair then failed three times with `LOCAL_COMMIT_FAILURE`; the inspected repair
  worktree had no uncommitted changes. A no-op repair being treated as a failed
  commit is a likely cause, not yet confirmed from the underlying exception.
- Run ended BLOCKED. The required verification gate remained PENDING.
- No PR or automatic merge was completed; no approval or evidence was fabricated.
- Follow-up: inspect the underlying commit exception, handle no-op repairs safely,
  and improve review evidence/context. Retry only after addressing these failures.

## Local runner corrections

- Reviews now receive bounded, redacted integrated source, prioritizing changed
  files. This exposes unchanged return mappings that a three-line diff omitted.
  The prompt distinguishes static assessment from executed verification evidence.
- Guarded REPAIR tasks may record an originally empty commit when validated output
  matches the existing tree. Integration preserves these explicit repair markers;
  redundant nonempty cherry-picks and conflicts still fail. Empty implementation
  commits remain rejected. Markers do not imply review or verification approval.
- Regression coverage exercises a no-op provider patch, unchanged implementation
  rejection, marker integration, and inclusion of source contracts in review input.
- Full runner suite: 376 tests, zero failures. Live retry remains separate evidence.
- Rebuilt and replaced the local Windows preview JAR, preserving enrollment and
  provider settings. Installed SHA-256:
  `d524815547ca66b2deae2c95d6890f9fe2658f67548d3a846663ca31c4dffede`.
  No server deployment, push, release, or direct run-state reset performed.

## Review-format retry

The updated runner completed repair and integration. Subsequent reviews failed
exact criterion identity validation. The server stores nine distinct criteria;
the generic review schema allowed the model to rewrite their statements. Review
requests now supply canonical JSON strings and a task-specific schema constraining
each statement to the server list and the assessment count to the list length.
The existing exact-once, duplicate, and approval consistency checks remain intact.
Focused review/schema tests passed; a new successful live result is still required.

## Completion and unattended follow-up

- Issue #6 completed and PR #7 merged on 2026-10-09 at 17:20:55 UTC, after
  a manual approval. This demonstrates verified delivery, not unattended approval.
- Both current policy and its captured snapshot disabled human approval and enabled
  auto-merge. An unconditional delivery approval check ignored that setting.
- Fixed snapshot-based authorization plus scheduled verified publication;
  364 control-plane tests passed, zero failures/errors/skips.
- Deployed the tested JAR to the server for the authorized follow-up run, retaining
  the prior image as `forgeloop-control-plane:before-approval-fix-20261009`.
  Deployed JAR SHA-256: `7babbbde09ade5b1be16c54e95bf456c2b64e061964e04eb563bf8f2ed1cadad`.
  Server health was healthy and the public site returned HTTP 200. No Git push.
- Fresh issue #8 documents Search's return contract; run
  `4846e066-107c-4490-8a2c-df2622fbb67d`, USD 5 budget, no manual approval.
- Review falsely included changes from already-merged #6 because it used cached
  local `main`, while execution correctly used refreshed `origin/main`.
  The run became BLOCKED after three review failures; verification stayed PENDING.
- Corrected review's diff/context base to the same remote-tracking ref used by
  execution. Added a real-Git regression proving old merged changes are excluded.
  Focused runner tests/build passed; updated the Windows preview after it blocked.
- User must start that updated runner and grant Retry run through the dashboard.
  Unattended merge validation is still pending; no run state has been reset manually.

### Subsequent review retry

The corrected diff base excluded previously merged changes. Review confirmed the
comment-only scope, but rejected missing execution evidence before the execution
verification stage had run. Clarified that static review must identify concrete
code defects and mark command execution pending; mandatory verification still
blocks delivery until actual checks pass. Rejected review also threw after closing
its lease, causing the generic catch to complete the same lease twice and report
`Lease must be active and acknowledged before completion`. Removed that redundant
throw: rejection remains a completed negative result, not another harness failure.
Focused review/runner/Git tests and build passed; local preview updated again.

## Successful unattended validation

- Fresh project-hub issue #10 added regression coverage for non-string search
  queries; run `46eb27db-97f1-42b4-9d78-59c5343fa078`, USD 5 ceiling.
- Run reached COMPLETE. `approved_at` and `approved_by` are both NULL: no human
  approval was recorded.
- Required `unit` gate PASSED, including Node tests and the production build.
- PR #11 automatically merged at 2026-10-09 12:09:22 America/Los_Angeles
  (19:09:22 UTC), commit `fbae689ee2dab57a0118acdb89cadaa936b235af`.
- Source issue #10 is CLOSED; server reconciliation recorded the automatic merge.
- Recorded known provider cost: USD 0.218838. Some provider cost telemetry is
  unknown; this figure is not a verified complete spend total.
- This closes the second-repository unattended delivery validation, not the other
  deferred production checks (restore, alert delivery, burn-in, and go/no-go review).
