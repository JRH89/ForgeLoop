# ForgeLoop completion roadmap

## Phase 0 — Reset the product boundary

Move the existing ticket-SaaS scaffold into the independently initialized `C:\Users\jrh89\Work\Ticketly` repository. It must have separate Git history, CI, environment configuration, issue tracker, GitHub App installation, and release lifecycle; it is not a ForgeLoop package or submodule. Replace the ForgeLoop ticket UI with the real operator console. Replace fabricated run metrics with persisted `FeatureRun` records. Ticket assignment remains a Ticketly issue only, never a ForgeLoop feature.

Exit criteria: ForgeLoop UI has no ticket CRUD; Ticketly runs independently; Git reports it as a distinct repository; Ticketly is one installed ForgeLoop GitHub App repository; and every connected repository has a documented versioned policy specifying labels, default branch, required checks, permitted paths, test commands, and PR policy.

## Phase 1 — Control-plane foundation

Create a Spring Boot control-plane service with PostgreSQL/Flyway, OIDC RBAC, structured error responses, audit ledger, configuration validation, health/readiness, OpenTelemetry tracing, and GraphQL API. Define migrations and repositories for every entity in the product specification.

Exit criteria: restart-safe run/task/gate state, authenticated operator access, migration tests, and immutable audit records.

## Phase 2 — GitHub App intake and PR reconciliation

Implement GitHub App installation flow, encrypted installation metadata, webhook signature verification, delivery idempotency, issue-label policy, repository policy sync, harness-profile detection, branch discovery, check-run reporting, PR creation, and reconciliation workers. Repository policy and the selected harness profile, not the Ticketly application shape, determine task roles and verification gates.

Exit criteria: a signed issue event from any installed repository creates exactly one run; duplicate deliveries create none; a passing synthetic run creates one draft PR in its source repository; and the control plane records the source issue, branch, commit SHA, policy revision, and PR URL.

## Phase 3 — Isolation and execution substrate

Implement worktree allocator, branch naming, filesystem quotas, container runner, image allow-list, network policy, command allow-list, lease expiry, cancellation, cleanup, and orphan recovery. Persist all resource identities and command/evidence digests.

Exit criteria: concurrent tasks get isolated worktrees/containers; forced worker termination is recovered safely; no task can access another task worktree or host secrets.

## Phase 3A — Distributed ForgeLoop Runner

Build a separately versioned, locally installable runner. It registers to an organization with a single-use token, publishes signed capabilities and heartbeats, claims only matching task leases, verifies lease expiry/signature, provisions local worktrees and containers, streams redacted events, uploads checksummed artifacts, and submits idempotent results. Start with a container image and a cross-platform CLI installer.

The runner must support `LOCAL_RUNNER` for individual developers and `SELF_HOSTED_RUNNER` for customer servers/VPCs. It must use runner-managed provider and GitHub credentials for MVP; the control plane receives only provider availability/capability metadata and usage totals.

Exit criteria: two runners on separate machines can register to one organization, claim distinct compatible tasks concurrently, survive reconnects without duplicate execution, and leave no cloud copy of provider keys or repository source by default.

## Phase 4 — Provider and worker contracts

Implement provider-neutral interfaces and adapters for Anthropic, OpenAI-compatible, Gemini, and local compatible endpoints. Add planner, backend, frontend, independent-test, integration, repair, and review worker roles. Require JSON-schema validated output and prompt/output redaction.

Exit criteria: a provider can be swapped by policy without changing scheduler code; malformed provider output is classified, retried within limits, and never marked successful.

## Phase 4A — MCP discovery and context routing

Implement runner-local MCP registration for filesystem, Git, Docker, testing, browser, and repository tools; implement remote MCP registration for GitHub, issue trackers, observability, and documentation. Add tool manifests, permission grants, context-size budgets, audit events, health checks, and per-task allow-lists.

Exit criteria: a worker receives only approved tools for its task; a local filesystem tool is invoked only by the assigned runner; remote tool failures become evidence, not hidden prompt text.

## Phase 5 — Planning, DAG scheduling, and repair

Implement acceptance-criteria extraction, task graph validation, path/capability conflict detection, dependency scheduling, worker leases, cancellation, repair-package builder, and fixed repair budget policy.

Exit criteria: backend/frontend/test tasks execute concurrently when safe; a controlled test failure routes only to the responsible repair task; exhaustion blocks the run with evidence.

## Phase 6 — Verification and evidence

Implement hermetic verifier images and gates for Maven, frontend tests/typecheck/lint, GraphQL contract tests, Compose health, Playwright, dependency/security scans, and spec-coverage review. Write an append-only evidence bundle to object storage or a filesystem implementation with checksums.

Exit criteria: deleting or corrupting an artifact is detected; a run cannot reach `READY_FOR_REVIEW` without every required gate and criterion evidence.

## Phase 7 — Operator experience and MCP

Build the real operator console: intake queue, run timeline, DAG, task attempts, stream logs, cost/budget, approval queue, evidence browser, repair reasoning, and PR link. Make MCP a gateway to permissioned control-plane operations, not a static instruction server.

Exit criteria: an operator can diagnose, cancel, retry, and approve a run without database or shell access; every action reaches the audit ledger.

## Phase 8 — End-to-end target scenario

Run FEATURE-142 as a real GitHub issue against Ticketly, the separate example SaaS repository. Verify webhook delivery, the created branch, GraphQL authorization, UI behavior, audit event, container health, Playwright scenario, required checks, and generated draft PR. Then repeat a smaller issue against a second repository profile to prove ForgeLoop has no Ticketly-specific orchestration path.

Exit criteria: a fresh issue in Ticketly and a fresh issue in a second repository profile each complete without manual code edits by the operator; their run records retain their independent policy and harness-profile evidence.

## Phase 9 — Production hardening and launch

Provision managed PostgreSQL/object storage, secret manager, OIDC, GitHub App, container registry, sandbox runners, TLS, backup/restore, monitoring, alerting, rate limits, retention, incident runbooks, dependency/image scanning, SLOs, and disaster-recovery exercises.

Exit criteria: staging burn-in, controlled pilot repositories, restoration drill, security review, load/concurrency test, and documented production go/no-go approval.

## Scale and commercial execution boundary

Do not build ForgeLoop-hosted arbitrary-code execution in the first release. It requires ephemeral VM/container provisioning, repository trust classification, egress controls, secret injection, autoscaling, image supply-chain controls, abuse prevention, and cost isolation. Add it only after self-hosted runner pilots prove the control-plane, lease, artifact, and policy model.

## Required validation matrix

* Unit: state transitions, policy decisions, provider parsing, redaction, retries, idempotency.
* Integration: Postgres migrations, GitHub webhook signatures, GitHub API fakes, provider fakes, worktree leases, container runner.
* Contract: GraphQL schema, webhook payloads, MCP schemas, provider normalized response.
* End-to-end: issue → plan → parallel workers → controlled failure → repair → verification → draft PR.
* Security: authorization matrix, secret-scan fixtures, path traversal, command injection, webhook replay, tenant isolation.
* Operational: restart recovery, orphan cleanup, dead-letter retries, backpressure, budget exhaustion, artifact corruption, provider outage.
