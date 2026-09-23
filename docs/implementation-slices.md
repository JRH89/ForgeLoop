# ForgeLoop implementation slices

This is the execution plan for the production product, not a demo plan. A slice is complete only when its exit criteria, automated tests, documentation, targeted merge, and remote push are complete. Work proceeds slice-by-slice; partial components do not count as a slice completion.

## Current position

ForgeLoop has a working local control plane, PostgreSQL/Flyway migrations, signed GitHub webhook intake, installed-repository synchronization, live GitHub App JWT and installation-token validation, runner registration/leases, validated task-DAG execution and integration, repository-policy-selected container verification, criterion coverage, redacted checksummed evidence bundles, a role-aware operational console, a permissioned MCP gateway, persisted tenant roles, OIDC issuer/audience enforcement outside explicit development mode, production configuration validation, and tenant-scoped digest-only control-plane audit events.

Ticketly is now a separate support SaaS repository. It has no ForgeLoop delivery dashboard, fabricated agent runs, fabricated cost data, or fabricated verification data. It remains the primary target application for the eventual real GitHub issue-to-PR validation.

The product can invoke policy-selected models with schema-constrained output, create guarded task-scoped commits, integrate declared changes, push an exact runner-produced head with a lease-bound GitHub credential, perform independent criterion-level review, dispatch immutable repository-policy verification tasks, and route failed quality gates through a fresh code-repair task. Ticketly issue 7 completed that full path and ForgeLoop created check run and draft PR 8 after explicit approval; the PR was subsequently merged. Object-store artifacts and a second unrelated repository proof remain incomplete.

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

Progress evidence: the Ticketly GitHub App installation has been authenticated with a real App JWT and installation token, synchronized from GitHub, and a signed `issues` webhook created Ticketly issue run `issue-1`. The remaining exit criterion depends on Slice 3 producing a verified change for the existing idempotent GitHub delivery adapter.

## Slice 3 — Runner execution lifecycle

Make the self-hosted runner safely execute assigned work, not merely verify an operator-supplied command.

- Add runner installation packaging, secure local configuration, capability attestation, polling/streaming dispatch, cancellation, and reconnect semantics.
- Allocate task worktrees, deterministic branch names, resource quotas, cleanup/orphan recovery, image allow-lists, command allow-lists, and redacted event streaming.
- Add artifact upload to a storage abstraction with checksums, retention metadata, and corruption verification; retain the existing filesystem bundle implementation as the local backend.
- Enforce idempotent result submission and prevent late/expired workers from overwriting current state.

Exit criteria: two runners claim distinct compatible tasks, a killed runner is recovered safely, worktrees are cleaned, and artifacts verify before the control plane accepts results.

Implementation progress: dispatch now gives authenticated runners structured, policy-derived repository, base-branch, source-reference, and capability context. The runner can claim one eligible task and prepare an isolated detached worktree from only a pre-cloned checkout beneath its configured repository root. Automatic provider execution, policy-selected commands, and lifecycle reconciliation remain in progress.

## Slice 4 — Provider contracts and guarded agent workers — complete

Implement real coding-agent execution as a replaceable, runner-local capability.

- Define normalized provider contracts, model-selection policy, token/cost records, JSON-schema output validation, timeout/retry classification, and redaction.
- Implement OpenAI-compatible and Anthropic adapters first; add Gemini/local adapters behind the same contract.
- Define least-privilege manifests for planner, implementation, backend, frontend, independent-test, integration, repair, and review roles. Execute code-producing roles through the guarded patch worker; task-graph and integration coordination remain in Slice 5.
- Keep provider keys and repository source on the runner; persist only redacted metadata, digests, usage, and evidence references.

Exit criteria: a provider can be replaced by policy; malformed or timed-out provider output cannot advance work; a provider outage creates actionable evidence rather than a false success.

Completed evidence: runner-local Anthropic Messages, OpenAI Responses, Gemini generateContent, and local OpenAI-compatible adapters share a normalized contract. A strict runner-local role policy chooses the adapter, model, and one-to-three-attempt retry budget without storing credentials in the control plane. Code-producing roles validate an exact JSON patch schema, reject duplicate or out-of-policy paths and symbolic-link traversal, use atomic file replacement, and commit only inside an isolated worktree. Every authenticated execution records redacted request digests, exact attempt count, token usage, explicit known/unknown cost, outcome, and failure category through its active lease. A valid patch stops at `CHANGE_READY`; it cannot claim verification. Malformed output and provider exhaustion fail the lease with no false success. Automated evidence: 46 runner tests, 52 control-plane tests, a healthy Compose deployment bootstrapped from an empty database through Flyway schema 15, and a live Anthropic credential health check.

## Slice 5 — Planner, DAG scheduler, and bounded repair loop — complete

Connect a specification to coordinated multi-agent work.

- Extract acceptance criteria and generate a validated task graph with explicit dependencies, path ownership, capability requirements, and budgets.
- Schedule independent tasks concurrently only when dependency and path-conflict checks allow it.
- Route failures to a bounded repair package that contains only the relevant diff, logs, policy, and criteria.
- Track attempts, budget/cost, transitions, cancellation, and terminal failure consistently across runs, tasks, and gates.

Exit criteria: a controlled failure repairs only its owning task; attempt/budget exhaustion blocks the run; all graph transitions are restart-safe and tested.

Completed evidence: the runner's planner role now accepts only an exact JSON plan contract and performs semantic validation before submission; the control plane repeats validation transactionally before materializing criteria, stable task keys, dependencies, owned paths, capabilities, attempt limits, and micro-dollar budgets. Run-scoped pessimistic locking makes competing claims restart-safe, while dependency, path-overlap, capability, task-budget, and run-budget checks determine dispatch eligibility. Independent backend/frontend nodes are concurrently eligible; dependent integration and verification nodes remain blocked. Integration cherry-picks only server-declared dependency SHAs and atomically marks only those dependencies integrated. Failed or expired work increments the owning task's fixed attempt budget, creates bounded repair context from change/evidence identities and criteria, redispatches under the `REPAIR` role, and blocks the run after exhaustion. Automated evidence includes cycle/traversal/budget rejection, dependency and conflict scheduling, repair isolation/exhaustion, integration state transitions, real Git integration, an authenticated live planner-lease/DAG submission, and a clean PostgreSQL migration through Flyway schema 16.

## Slice 6 — Policy-selected verification and evidence — complete

Make verification authoritative for every repository profile.

- Persist repository commands, image digests, network policy, browser scenarios, security scans, contract checks, and criterion-to-evidence mappings.
- Dispatch required verification gates through runners; record passed, failed, timed-out, skipped-by-policy, and manual-override states.
- Build evidence bundles that contain checksums, command metadata, output/artifact references, and provenance without raw secrets.
- Enforce that `READY_FOR_REVIEW` requires all required gates and criterion evidence; failed gates create the appropriate repair package.

Exit criteria: artifact corruption is detected; a skipped/failed gate cannot create a PR; Ticketly’s backend, frontend, Compose, Playwright, and authorization checks are all selected by policy rather than hard-coded.

Completed evidence: repository connections now persist versioned gate definitions containing check kind, digest-pinned image, argv, network decision, timeout, required status, and criterion mapping; each run receives an immutable snapshot and planner completion creates dependency-gated `VERIFICATION` tasks. The runner routes those tasks without provider-policy lookup, verifies the integrated ref in a disposable container with a read-only source mount and executable ephemeral workspace, bounds runtime/output/storage, and defaults network to none. Reports redact common credentials and include command/image provenance, timestamps, artifact reference, output digest, and canonical bundle digest; the control plane independently recomputes checksums and rejects corrupt, secret-bearing, or policy-mismatched submissions. Required gate states include passed, failed, timed out, skipped by policy, and audited manual override, while review readiness additionally requires completed verification leases and all mapped required evidence. Ticketly policy revision 3 independently selects backend Maven, frontend lint/unit/typecheck/build, Compose contract, Playwright scenario discovery, and authorization contract gates using immutable container digests. Local proof includes corruption rejection through authenticated GraphQL, successful Ticketly backend/frontend/Compose/Playwright workloads, 66 control-plane and 50 runner test cases, frontend/MCP/harness suites, browser E2E, and clean PostgreSQL migration through all 18 Flyway migrations to schema 17.

## Slice 7 — Operator console and MCP gateway — complete

Deliver the operational product surface.

- Build intake queue, run timeline, DAG/attempt view, live redacted logs, evidence browser, budgets, approvals, cancellation/retry controls, and PR links.
- Replace direct operator GraphQL mutations that bypass workflow with role-aware actions and confirmation states.
- Build MCP as a permissioned gateway to these control-plane operations, with tool grants, audit records, and no repository command execution in the cloud service.

Exit criteria: an authorized operator can diagnose, cancel, retry, approve, and inspect a run without shell/database access; every action is auditable.

Completed evidence: the React console now provides a persisted intake queue, five-second run refresh, task DAG and dependency/attempt/repair views, acceptance criteria, policy gates, provider tokens/costs, run budgets, redacted evidence output with provenance, audit timeline, and direct PR links. Viewers are read-only, operators can confirm cancellation and bounded retry, and administrators can explicitly approve only `READY_FOR_REVIEW` runs; GitHub delivery rejects a verified but unapproved run. The generic task-state mutation was removed from GraphQL. The MCP server now discovers only environment-granted tools, invokes static GraphQL documents with bearer identity, filters undeclared arguments, requires HTTPS outside localhost, defaults to read-only grants, and exposes no repository command execution. Verification includes 69 control-plane tests in the production image build, two frontend unit tests, four MCP tests, frontend and MCP type checks, frontend lint/build, a live PostgreSQL migration to schema 18 with Hibernate validation, healthy Compose services, a live operator GraphQL contract query, and the browser operator-console smoke test.

## Slice 8 — End-to-end proving ground

Prove repo-agnostic delivery using separate repositories.

- Configure Ticketly’s GitHub App installation and versioned ForgeLoop policy.
- Execute a real Ticketly issue through issue intake, planning, agent work, repair (including one intentionally controlled failure), verification, draft PR, and reconciliation.
- Repeat with a second unrelated repository profile to prove no Ticketly-specific behavior exists.
- Preserve redacted evidence and a reproducible test report for both runs.

Exit criteria: both repositories complete from fresh GitHub issues without manual repository edits by an operator, and their policies/evidence remain independent.

Progress evidence: Ticketly issue 7 entered through the signed public GitHub App webhook, produced a schema-constrained plan, generated and integrated commit `044e5067cb9c94d353baf1b9bcd2e377a077ebe6`, pushed it with a lease-bound installation token, passed independent criterion review, and passed the authorization, backend, browser, Compose, and frontend policy gates with checksummed evidence. Explicit ForgeLoop approval created a successful GitHub check and draft PR 8; both GitHub checks passed and the PR merged as `73476b5964e4ce65b08bbed33f151def2493a6f5`. The remaining Slice 8 exit criterion is the same fresh-issue proof against a second installed, unrelated repository.

## Slice 9 — Production launch readiness

Prepare the verified product for a staging burn-in and production decision.

- Provision managed PostgreSQL, Redis/queue, object storage, secrets manager, OIDC, GitHub App, container registry, TLS, monitoring, alerting, backups, and restore drills.
- Add dependency/image scanning, SBOMs, rate limits, structured logs/traces/metrics, SLOs, incident runbooks, retention/deletion policy, load testing, and disaster recovery.
- Run security review, staging burn-in, controlled pilot, backup restore, and explicit go/no-go review.

Exit criteria: all operational checks have evidence, a restore drill passes, the pilot is stable, and no production claim is made before the external credentials/infrastructure have been supplied and validated.

## Execution discipline

For each slice: create a focused branch, implement the whole slice, add unit/integration/contract coverage, update the capability checklist and operations documentation truthfully, run the relevant build suite, merge with a targeted commit, and push `master`. External credentials are only required for Slice 2’s live GitHub execution and Slice 9’s hosted validation; all other implementation and fake/contract coverage can proceed without them.
