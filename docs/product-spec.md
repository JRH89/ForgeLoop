# ForgeLoop production product specification

## Product boundary

ForgeLoop is a software-delivery control plane. It accepts a GitHub issue or a structured specification, creates a bounded execution run, dispatches scoped engineering tasks to model providers in isolated Git worktrees and containers, verifies resulting changes, routes failures to bounded repairs, and creates a pull request only when required evidence passes.

ForgeLoop is repository-agnostic. A connected repository is an independently managed GitHub repository with its own Git history, CI, issue tracker, release lifecycle, credentials, and GitHub App installation. ForgeLoop never includes a customer repository as a package, submodule, or source dependency. Ticketly, located in the separate `C:\Users\jrh89\Work\Ticketly` repository, is only the first validation target for the portfolio scenario; it receives no product-specific behavior or code path.

For every connected repository, an allow-listed GitHub issue is delivered through the installed GitHub App; ForgeLoop creates a run, a runner clones that repository, performs work on a new branch, verifies the change, and opens a draft pull request back to that same repository. Ticketly is used to prove this path end to end, not to define it.

## Deployment architecture: cloud control plane and distributed runners

ForgeLoop is web-first. The cloud control plane owns identity, organizations, repository policy, run/task state, scheduling, event streaming, GitHub integration, usage metrics, and artifact metadata. It does **not** clone customer repositories or execute untrusted builds by default.

Execution happens on a ForgeLoop Runner installed on a developer machine, customer server, or customer VPC. The runner owns repository checkout, Git worktrees, Docker, builds, tests, Playwright, browser execution, local MCP servers, and model calls. This keeps source code and runner-managed provider credentials inside customer-controlled infrastructure.

```
Browser -> React operator console -> Spring control plane -> PostgreSQL / queue / artifact metadata
                                            |
                                            | authenticated task lease and event stream
                                            v
                              ForgeLoop Runner on customer infrastructure
                          Git worktrees / containers / tests / Playwright / MCP
                                            |
                                            v
                                Anthropic / OpenAI / Gemini / local provider
```

Initial execution modes are `LOCAL_RUNNER` and `SELF_HOSTED_RUNNER`. A ForgeLoop-hosted ephemeral runner is a future commercial execution mode and is explicitly out of scope for the first production release.

## User outcomes

| User | Outcome |
| --- | --- |
| Repository maintainer | Connects a repository and allows selected issue labels to start a run. |
| Delivery operator | Sees run state, task ownership, live logs, evidence, costs, and a clear intervention queue. |
| Reviewer | Receives a pull request with traceable acceptance-criteria evidence. |
| Platform owner | Controls providers, models, budgets, concurrency, permissions, retention, and rollout policy. |

## Required workflow

1. GitHub App webhook receives an allow-listed issue event or an operator submits a specification.
2. Intake validates repository policy, branch, budget, issue body, and idempotency key.
3. Planner derives structured acceptance criteria, risks, dependencies, and a DAG of bounded tasks.
4. Scheduler reserves a worktree and disposable execution environment per task.
5. Workers receive only task context, repository rules, permitted MCP tools, a provider/model selection, and explicit output schema.
6. Integration creates a candidate branch and combines completed task changes through reviewed conflict handling.
7. Verification runs compilation, unit/integration tests, contract checks, container health, browser tests, security checks, and acceptance-criteria review.
8. A failed gate creates a repair package for its owning task. Repairs are capped by policy.
9. A run is `READY_FOR_REVIEW` only when every required gate has passing evidence. A reviewer or policy-approved bot creates the PR.
10. GitHub PR status, commits, review comments, and merge outcome are reconciled into the immutable run ledger.

## State machines

### Run

`RECEIVED → VALIDATING → PLANNING → QUEUED → EXECUTING → INTEGRATING → VERIFYING → REVIEWING → READY_FOR_REVIEW → PR_OPEN → COMPLETE`

Terminal non-success states: `REJECTED`, `BLOCKED`, `CANCELLED`, `FAILED`. A failed verification moves to `REPAIRING`; repair-budget exhaustion moves to `BLOCKED`. No agent may transition a run directly to success.

### Task

`PENDING → LEASED → PREPARING → RUNNING → CHANGE_READY → INTEGRATED → VERIFIED`

Failure transitions are `RETRYABLE_FAILURE`, `REPAIR_QUEUED`, `HELD`, and `FAILED`. A worker lease expires; a late worker result cannot overwrite a newer attempt.

### Verification gate

`PENDING → RUNNING → PASSED | FAILED | TIMED_OUT | SKIPPED_BY_POLICY`. `SKIPPED_BY_POLICY` disqualifies automatic PR creation unless an explicit signed override is recorded.

## Domain model

| Entity | Essential fields |
| --- | --- |
| `RepositoryConnection` | installation id, repository id, default branch, policy revision, enabled status |
| `RepositoryPolicy` | issue labels, branch rules, harness profile, allowed paths, required checks, permitted tools, budgets, PR policy |
| `FeatureRun` | id, source issue/spec, repository, branch, state, budget, timestamps, correlation id |
| `AcceptanceCriterion` | run id, statement, priority, verification mapping, coverage state |
| `Task` | run id, dependency ids, scope paths, capability, owner, state, attempt budget |
| `WorktreeLease` | task id, path, base SHA, branch, container id, expiry, cleanup state |
| `AgentAttempt` | task id, provider/model/reason, prompt digest, input/output artifact refs, tokens, cost, exit state |
| `VerificationEvidence` | run/task id, gate, command digest, exit code, artifact refs, executor image digest |
| `RepairPackage` | failed gate, owning task, relevant diff/log/context references, attempt count |
| `PullRequestRecord` | repository PR id, branch SHA, URL, check status, merge reconciliation |
| `AuditLedgerEntry` | immutable actor/action/correlation/time/payload digest |
| `Runner` | organization id, public key, labels/capabilities, version, heartbeat, state, trust policy |
| `RunnerRegistrationToken` | single-use encrypted registration secret, expiration, organization scope |
| `TaskLease` | task id, runner id, nonce, expiry, capability grant, acknowledgement/result state |
| `RunnerCredentialMode` | runner-managed, managed-secret reference, provider policy, permitted models |
| `Artifact` | checksum, content type, size, retention class, encrypted storage reference, evidence owner |

## API and integration contracts

The control plane exposes GraphQL for the operator UI and REST webhook endpoints for GitHub. GitHub uses App installation credentials, webhook signature validation, delivery-id idempotency, least-privilege permissions, and reconciliation polling. Each run resolves its checks and commands from a versioned `RepositoryPolicy` or detected harness profile; no framework or target-application name is hard-coded into the orchestration path. Provider credentials are stored outside the database and injected only into short-lived workers.

MCP tools are capability-specific and sandboxed: repository context, schema access, test execution, container inspection, failure-log retrieval, and evidence submission. There is no arbitrary shell MCP tool.

Runner registration uses a one-time organization-scoped token and mutually authenticated runner session. The runner polls or maintains a reconnecting stream for task leases, validates the lease signature and expiry, acknowledges before execution, streams redacted events, uploads checksummed artifacts, and submits an idempotent result. Lost runner leases expire and are eligible for safe rescheduling only after worktree/branch reconciliation.

MCP routing is split by location. Local MCP services—filesystem, Git, Docker, test, browser, and repository-specific tools—run beside the runner and are never exposed to the public control plane. Remote MCP services—GitHub, Linear, Sentry, documentation, and policy services—are registered by endpoint, permission scope, and context policy. ForgeLoop orchestrates discovery, authorization, and routing; it does not require hosting every MCP server.

## Provider policy

Providers implement one normalized contract: structured task result, tool-call transcript, token/cost accounting, retryable error classification, and cancellation. Initial adapters are Anthropic, OpenAI-compatible, Gemini, and local OpenAI-compatible endpoints. Model selection is deterministic from task capability, policy, budget, provider health, and explicit rationale stored with every attempt.

The MVP defaults to runner-managed credentials: the runner reads provider and GitHub credentials from its own secret store or environment, and the control plane never receives raw key material. Managed credentials are an optional later mode using envelope encryption and short-lived runner grants.

## Security and reliability requirements

* GitHub webhook HMAC verification, replay protection, and idempotency.
* OIDC-authenticated operator UI with repository-scoped RBAC.
* Per-tenant/repository isolation; secrets never appear in prompts, logs, evidence, or browser artifacts.
* Non-root, network-restricted, resource-limited disposable worker containers.
* Worktrees are scoped to one task and cleaned only after evidence retention succeeds.
* Runner registration, runner capability labels, lease signatures, lease expiry, and artifact checksums are verified server-side.
* Runner events are redacted before upload; raw prompts, source files, provider keys, and filesystem paths are not stored in cloud logs by default.
* Budget, timeout, concurrency, and repair limits are enforced server-side.
* Append-only audit ledger and object-store evidence with retention/deletion policies.
* PR creation requires verified base SHA, passing required gates, and a policy decision.
* Failed external operations use idempotency keys and reconcile to `UNKNOWN` rather than assuming success.

## Completion definition

ForgeLoop is complete for its first production release when it can autonomously process an allow-listed GitHub issue from any installed repository through a verified PR in that repository, with all artifact paths, model selections, costs, test outcomes, repair attempts, and acceptance-criteria coverage visible in the operator UI and recoverable after restart. Ticketly FEATURE-142 is the first end-to-end validation, followed by a second repository-profile validation.
