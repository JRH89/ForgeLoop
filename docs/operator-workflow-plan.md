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

The local runner image `forgeloop-runner:operator-workflow` is built. Replacement
of `forgeloop-slice8-runner` was blocked by the execution tool, so the original
container, credentials and state remain unchanged. Upgrade that installation
before the next funded proving run. No provider calls were made; all 36 historical
requests remain unpriced because the prior installation had no model rates.
