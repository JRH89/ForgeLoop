# Operator workflow completion

- [x] Self-service runner enrollment, downloadable tested runner package, secure local key setup and restart instructions.
- [x] Reversible run archiving with tenant/role enforcement and active-work protection.
- [x] Repository intake setting requiring a named GitHub assignee, in addition to the intake label.
- [x] Explicit per-model pricing configuration and truthful known/unknown cost presentation.
- [x] Automatic queue/detail refresh, connection status, and more frequent redacted runner progress events.
- [x] Regression tests, guide updates and local deployment checks.

Release verification and merge are tracked by [PR 19](https://github.com/JRH89/ForgeLoop/pull/19).

Do not spend provider credits or modify existing user runs to demonstrate these features.
Archive preserves audit/evidence and issue deduplication; permanent erasure is not queue cleanup.

Implementation verification: frontend lint, 14 tests, typecheck and build; runner
and control-plane Docker builds execute their complete Maven suites; Windows
offline installer verifies stdin enrollment, DPAPI storage, pricing and startup.
The deployed database migrated to schema 26 with all 10 existing runs retained.
Public runner ZIP and checksum respond successfully. CI also tests real packaged
enrollment/heartbeat against a disposable stack without any paid model calls.

Known boundaries: prerequisites are installed separately; Linux/macOS use the
packaged Java CLI, while the interactive installer/start-at-login integration is
Windows-specific. Historical unpriced usage remains unpriced. Rates are supplied
by the operator and are estimates, not billing receipts. Existing containers need
the updated runner image to emit new progress events.

The local runner was upgraded to `forgeloop-runner:operator-workflow` on
2026-09-26. Its identity, credentials, command and state mounts were preserved;
the replacement heartbeat was accepted and no tasks were available. The original
container is stopped with restart disabled under
`forgeloop-slice8-runner-before-operator-workflow` for rollback. Never start both
containers together because they share an identity and state.

The replacement mounts the ignored local `provider-policy-priced.json`, retaining
the original policy for rollback. All eight Sonnet 5 roles use standard estimates
of $2 input / $10 output per million tokens, verified against
[Anthropic pricing](https://platform.claude.com/docs/en/about-claude/pricing).
No funded proving run was started. The 36 historical requests remain unpriced
because the prior installation had no model rates; configured prices apply to
future requests, not retroactive billing receipts.
