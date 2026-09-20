# ForgeLoop implementation slices

This is the execution plan for the production product, not a demo plan. A slice is complete only when its exit criteria, automated tests, documentation, targeted merge, and remote push are complete. Work proceeds slice-by-slice; partial components do not count as a slice completion.

## Current position

ForgeLoop has a working local control plane, PostgreSQL/Flyway migrations, signed GitHub webhook intake, installed-repository synchronization, runner registration/leases, isolated container verification, named gate state transitions, local checksummed evidence bundles, a basic operator console, persisted tenant roles, OIDC issuer/audience enforcement outside explicit development mode, production configuration validation, and tenant-scoped digest-only control-plane audit events.

Ticketly is now a separate support SaaS repository. It has no ForgeLoop delivery dashboard, fabricated agent runs, fabricated cost data, or fabricated verification data. It remains the primary target application for the eventual real GitHub issue-to-PR validation.

The product does **not** yet autonomously plan or modify repositories, invoke a model, create a branch/PR/check run, select and dispatch policy commands, persist object-store artifacts, enforce organizations/RBAC, or complete an end-to-end issue-to-PR loop. These are the critical path.

## Slice 1 — Multi-tenant security and auditable operations — complete

Deliver the security boundary that permits real organizations to use the control plane.

- Persist organizations, users, memberships, repository ownership, roles, and immutable audit-ledger records.
- Map validated OIDC claims to a user and organization membership; reject cross-organization access server-side.
- Apply authorization to every GraphQL query/mutation and GitHub installation action.
- Add a production configuration validator: OIDC issuer/audience, database settings, GitHub webhook secret, encryption key, storage configuration, and disallow development mode.
- Add audit queries for an operator’s run timeline without exposing source content, credentials, or raw prompts.

Exit criteria: authorization-matrix tests prove tenant isolation; production startup fails closed when mandatory configuration is absent; every operator mutation emits an immutable audit event.

Completed evidence: persisted organization membership role checks, tenant-scoped repository/run services, privileged-action audit coverage, a tenant-scoped run timeline query, production configuration tests, and 38 passing control-plane tests. Initial tenant-administrator bootstrap remains a controlled deployment task rather than an unauthenticated product endpoint.

## Slice 2 — GitHub App delivery lifecycle

Turn intake into a usable repository-delivery integration.

- Implement installation callback state validation and repository-policy review/update workflow.
- Add GitHub App JWT signing, installation-token exchange, encrypted installation metadata, API retry/backoff, and rate-limit handling.
- Create/reconcile feature branches, commits, check runs, draft PRs, review comments, and merge outcomes with idempotency keys.
- Restrict target paths, branches, and PR behavior through the versioned repository policy.
- Add GitHub API fake/contract tests for retries, duplicate deliveries, failed writes, and reconciliation.

Exit criteria: a signed issue event creates exactly one run, a synthetic completed change produces exactly one draft PR/check run, and all GitHub identifiers are persisted and reconciled.

## Slice 3 — Runner execution lifecycle

Make the self-hosted runner safely execute assigned work, not merely verify an operator-supplied command.

- Add runner installation packaging, secure local configuration, capability attestation, polling/streaming dispatch, cancellation, and reconnect semantics.
- Allocate task worktrees, deterministic branch names, resource quotas, cleanup/orphan recovery, image allow-lists, command allow-lists, and redacted event streaming.
- Add artifact upload to a storage abstraction with checksums, retention metadata, and corruption verification; retain the existing filesystem bundle implementation as the local backend.
- Enforce idempotent result submission and prevent late/expired workers from overwriting current state.

Exit criteria: two runners claim distinct compatible tasks, a killed runner is recovered safely, worktrees are cleaned, and artifacts verify before the control plane accepts results.

## Slice 4 — Provider contracts and guarded agent workers

Implement real coding-agent execution as a replaceable, runner-local capability.

- Define normalized provider contracts, model-selection policy, token/cost records, JSON-schema output validation, timeout/retry classification, and redaction.
- Implement OpenAI-compatible and Anthropic adapters first; add Gemini/local adapters behind the same contract.
- Build planner, implementation, independent-test, integration, repair, and review worker roles with least-privilege tool manifests.
- Keep provider keys and repository source on the runner; persist only redacted metadata, digests, usage, and evidence references.

Exit criteria: a provider can be replaced by policy; malformed or timed-out provider output cannot advance work; a provider outage creates actionable evidence rather than a false success.

## Slice 5 — Planner, DAG scheduler, and bounded repair loop

Connect a specification to coordinated multi-agent work.

- Extract acceptance criteria and generate a validated task graph with explicit dependencies, path ownership, capability requirements, and budgets.
- Schedule independent tasks concurrently only when dependency and path-conflict checks allow it.
- Route failures to a bounded repair package that contains only the relevant diff, logs, policy, and criteria.
- Track attempts, budget/cost, transitions, cancellation, and terminal failure consistently across runs, tasks, and gates.

Exit criteria: a controlled failure repairs only its owning task; attempt/budget exhaustion blocks the run; all graph transitions are restart-safe and tested.

## Slice 6 — Policy-selected verification and evidence

Make verification authoritative for every repository profile.

- Persist repository commands, image digests, network policy, browser scenarios, security scans, contract checks, and criterion-to-evidence mappings.
- Dispatch required verification gates through runners; record passed, failed, timed-out, skipped-by-policy, and manual-override states.
- Build evidence bundles that contain checksums, command metadata, output/artifact references, and provenance without raw secrets.
- Enforce that `READY_FOR_REVIEW` requires all required gates and criterion evidence; failed gates create the appropriate repair package.

Exit criteria: artifact corruption is detected; a skipped/failed gate cannot create a PR; Ticketly’s backend, frontend, Compose, Playwright, and authorization checks are all selected by policy rather than hard-coded.

## Slice 7 — Operator console and MCP gateway

Deliver the operational product surface.

- Build intake queue, run timeline, DAG/attempt view, live redacted logs, evidence browser, budgets, approvals, cancellation/retry controls, and PR links.
- Replace direct operator GraphQL mutations that bypass workflow with role-aware actions and confirmation states.
- Build MCP as a permissioned gateway to these control-plane operations, with tool grants, audit records, and no repository command execution in the cloud service.

Exit criteria: an authorized operator can diagnose, cancel, retry, approve, and inspect a run without shell/database access; every action is auditable.

## Slice 8 — End-to-end proving ground

Prove repo-agnostic delivery using separate repositories.

- Configure Ticketly’s GitHub App installation and versioned ForgeLoop policy.
- Execute a real Ticketly issue through issue intake, planning, agent work, repair (including one intentionally controlled failure), verification, draft PR, and reconciliation.
- Repeat with a second unrelated repository profile to prove no Ticketly-specific behavior exists.
- Preserve redacted evidence and a reproducible test report for both runs.

Exit criteria: both repositories complete from fresh GitHub issues without manual repository edits by an operator, and their policies/evidence remain independent.

## Slice 9 — Production launch readiness

Prepare the verified product for a staging burn-in and production decision.

- Provision managed PostgreSQL, Redis/queue, object storage, secrets manager, OIDC, GitHub App, container registry, TLS, monitoring, alerting, backups, and restore drills.
- Add dependency/image scanning, SBOMs, rate limits, structured logs/traces/metrics, SLOs, incident runbooks, retention/deletion policy, load testing, and disaster recovery.
- Run security review, staging burn-in, controlled pilot, backup restore, and explicit go/no-go review.

Exit criteria: all operational checks have evidence, a restore drill passes, the pilot is stable, and no production claim is made before the external credentials/infrastructure have been supplied and validated.

## Execution discipline

For each slice: create a focused branch, implement the whole slice, add unit/integration/contract coverage, update the capability checklist and operations documentation truthfully, run the relevant build suite, merge with a targeted commit, and push `master`. External credentials are only required for Slice 2’s live GitHub execution and Slice 9’s hosted validation; all other implementation and fake/contract coverage can proceed without them.
