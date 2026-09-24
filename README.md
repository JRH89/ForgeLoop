# ForgeLoop

**A distributed software engineering harness for autonomous coding agents.**

ForgeLoop turns feature specifications into verified software changes by decomposing work across parallel agents, executing those agents in isolated environments, and independently verifying their output with builds, tests, containers, browser automation, and acceptance criteria.

The goal is not to generate more code. The goal is to build the system around coding agents that makes autonomous software delivery reliable, observable, and repeatable.

## Delivery capability checklist

This checklist is maintained as implementation progresses. A checked item is implemented and has been verified locally; it does not imply every downstream production dependency is complete.

## GitHub repository connection

Copy `.env.example` to `.env`, create a GitHub App with the required repository permissions and webhook URL, then set `FORGELOOP_GITHUB_APP_SLUG` and `FORGELOOP_GITHUB_WEBHOOK_SECRET`. In the ForgeLoop console, select **Repositories** and choose **Install ForgeLoop GitHub App**. GitHub—not the user—supplies the installation identity after the App callback/repository-sync phase is configured.

## Open the operator dashboard

With the Compose stack running, open `http://localhost:5173`. The dashboard is ForgeLoop's operator surface: **Runs** shows the live task graph, attempts, evidence, audit trail, approval controls, and PR links; **Repositories** installs the GitHub App and displays synchronized repositories.

The named development tunnel deliberately exposes only GitHub webhook and setup-callback routes. It does not publish the dashboard or GraphQL API. A public dashboard requires the production OIDC configuration and a separately protected application hostname; see [Cloudflare Tunnel](docs/cloudflare-tunnel.md).

### Control plane

- [x] Spring Boot GraphQL control plane with persisted delivery-run, task, gate, criterion, and GitHub-delivery records.
- [x] React operator console with intake queue, task DAG/attempts, live polling, redacted evidence logs, acceptance gates, budgets, audit timeline, approval/cancel/retry controls, and GitHub PR links.
- [x] Permissioned MCP gateway calls static control-plane GraphQL documents with explicit tool grants and bearer identity; it exposes no cloud-side repository or shell execution.
- [x] Generic repository connection policy: installation ID, branch, issue label, harness profile, required gates, and budget enforcement.
- [x] Signed GitHub webhook endpoint with delivery idempotency and connected-repository label filtering.
- [x] Docker Compose deployment with PostgreSQL, control-plane health checks, and operator-console GraphQL proxy.
- [x] Flyway forward migrations verified against the local PostgreSQL control-plane database.
- [x] Persisted organization memberships and roles scope repository ownership, delivery-run visibility, runner enrollment, and privileged operator actions.
- [x] JWT `org_id` context is checked against persisted membership server-side; cross-organization repository access and runner-token issuance are rejected.
- [x] OIDC JWT issuer and audience boundary outside explicitly selected development mode, with digest-only audit records and tenant-scoped run timeline queries.
- [x] Production startup rejects missing OIDC audience, webhook secret, PostgreSQL, validated schema mode, artifact-storage URI, or encryption-key configuration.

### Runner and execution

- [x] Persisted runner identity, one-time 15-minute registration tokens stored as hashes, GraphQL registration, runner listing, and heartbeats.
- [x] Expiring, single-owner task leases with one-time runner nonce material stored only as a hash.
- [x] Runner-scoped credential issued once at registration, stored only as a hash by the control plane, and required for runner heartbeats.
- [x] Runner credential required to claim or acknowledge a task lease, in addition to the lease's one-time nonce.
- [x] Exact capability matching for runner discovery and server-side task-claim enforcement.
- [x] Scheduled lease-expiry recovery into the bounded repair queue.
- [x] Operator cancellation holds non-terminal tasks, records an audit event, and is exposed in the operator console.
- [x] Containerized local/self-hosted runner CLI with validated registration, persisted local identity, authenticated heartbeat, nonce-backed lease acknowledgement, authenticated task discovery, lease-bound automatic repository checkout, and policy-selected provider execution.
- [x] Runner-managed, task-scoped detached Git worktree creation with repository, path-traversal, duplicate, command-failure, and timeout guards.
- [x] Shell-free host verification executor constrained to task Git worktrees, bounded by timeout and output capture limits.
- [x] Disposable Docker verification executor that copies a read-only task mount into an ephemeral writable filesystem, bounds output/time/storage, requires digest-pinned images, and denies network access unless policy enables egress.
- [x] Authenticated, lease-bound persistence of redacted verification evidence with independently recomputed output/bundle digests, provenance, artifact references, and exact policy metadata matching.
- [x] Repository-policy-selected verification orchestration with server-created gate tasks, image/argv/network/timeout snapshots, criterion coverage, and bounded verification repair retries.
- [x] Lease-bound, checksummed artifact upload through filesystem and S3-compatible storage backends, with read-after-write verification, retention metadata, and operator-console visibility.
- [x] Lease-bound automatic GitHub clone/refresh using short-lived installation credentials kept out of arguments and logs.
- [x] Runner-local MCP stdio processes use organization-managed task routing plus a runner-owned command allowlist, bounded time and context, and no cloud-side process execution.
- [x] Lease-authenticated, redacted runner lifecycle events with idempotent sequence handling and a durable near-real-time operator timeline.
- [x] Runner-local Anthropic, OpenAI, Gemini, and local-model adapters; role/model policy; bounded retries; strict patch validation; guarded code-producing workers; and redacted token/cost/outcome persistence.
- [x] Strict planner output, bounded task-DAG scheduling, dependency/path conflict enforcement, parallel-ready dispatch, deterministic integration, and task-owned bounded repair routing.
- [x] Role-aware, confirmed, audited human cancellation, bounded task retry, and verified-run approval controls.
- [x] Independent review-agent orchestration with criterion-level immutable evidence.
- [x] Automated human escalation for attempt and spend boundaries, with tenant-scoped acknowledgement/resolution and audit history.

### Evidence and repository delivery

- [x] Required repository gates are dispatched as runner tasks; only matching passed evidence covers acceptance criteria and transitions a run to `READY_FOR_REVIEW`.
- [x] Local runner writes atomic, redacted JSON verification evidence with SHA-256 manifests and detects artifact corruption before upload or citation.
- [x] Container, browser, security, contract, and Compose checks are represented by repo-agnostic versioned policy; Ticketly's five-check profile is documented and persisted independently.
- [x] S3-compatible evidence upload with tenant/run/task/lease object identities, integrity verification, and explicit retain-until metadata; production bucket lifecycle/Object Lock remains a deployment responsibility.
- [x] GitHub App installation entry point; operators are redirected to the configured GitHub App rather than asked to enter an installation ID.
- [x] Signed, short-lived GitHub App callback state binds an installation to the initiating ForgeLoop organization before repository synchronization.
- [x] Signed GitHub App `installation_repositories` delivery synchronizes newly installed repositories into a conservative, configurable default policy without accepting a typed installation ID.
- [x] GitHub App JWT signing, short-lived installation-token exchange, idempotent branch/file/check-run/draft-PR delivery records, and transient GitHub API retry handling.
- [x] Live GitHub App credential validation, installation-token repository discovery, installation reconciliation, and signed GitHub Issue intake verified against the separate Ticketly repository.
- [x] Runner-produced verified changes wired into GitHub delivery, including a live draft-PR/check-run proof.
- [x] Ticketly end-to-end issue-to-verified-PR proof.
- [ ] Second unrelated repository profile proving repository independence.

### Current capability boundary

ForgeLoop can persist and operate policy-bound delivery runs and repository connections through its role-aware web console or permission-scoped MCP gateway. A self-hosted runner can execute a policy-selected planner, materialize a validated acyclic task graph, dispatch non-conflicting tasks, create schema- and server-path-validated commits, integrate declared dependency commits, push the exact integrated head with a lease-bound installation token, perform criterion-level independent review, and route failed review or verification through a bounded code-repair cycle. Required verification commands come from an immutable repository-policy snapshot and produce redacted, checksummed evidence; their JSON artifacts can be verified and retained in S3-compatible storage. A verified run requires explicit administrator approval before the control plane creates its check run and draft PR. Ticketly issue 7 proved this path live and produced merged PR 8 without operator repository edits. A second unrelated repository proof is deferred because the configured provider account has insufficient credit; hosted OIDC and production infrastructure validation also remain incomplete.

> **Specification → Plan → Parallel Agents → Integration → Verification → Repair → Review → Pull Request**

## Why ForgeLoop?

Coding agents are increasingly capable of implementing meaningful pieces of software, but generating a patch is only part of software engineering.

Production work also requires context, coordination, testing, integration, security boundaries, failure recovery, and evidence that the implementation actually satisfies the original requirements.

ForgeLoop treats those concerns as part of the harness.

An agent reporting that it finished a task does not make the task complete. Work is complete only when its required verification gates pass.

## How It Works

A ForgeLoop run begins with a feature specification.

For example:

> Add ticket assignment. Organization admins can assign tickets to members of their organization. Add the GraphQL API, authorization and validation, React interface, audit event, tests, and browser verification. Users must never be able to assign tickets to members of another organization.

ForgeLoop analyzes the repository and builds a targeted context package containing relevant architecture, source files, conventions, tools, tests, and project rules.

A planning agent converts the specification into a dependency graph of bounded tasks.

```text
                         Feature Specification
                                  │
                                  ▼
                         Repository Context
                                  │
                                  ▼
                            Planner Agent
                                  │
                                  ▼
                              Task DAG
                                  │
                   ┌──────────────┼──────────────┐
                   ▼              ▼              ▼
              Backend Agent  Frontend Agent   Test Agent
                   │              │              │
                   └──────────────┼──────────────┘
                                  ▼
                              Integration
                                  │
                                  ▼
                             Verification
                                  │
                           ┌──────┴──────┐
                           │             │
                         FAIL           PASS
                           │             │
                           ▼             ▼
                      Repair Agent    Review Agent
                           │             │
                           └──────↺      ▼
                                     Pull Request
```

Independent agents can work concurrently in isolated Git worktrees. Once their work is integrated, ForgeLoop verifies the resulting application rather than trusting agent output.

Failed verification is converted into structured context and routed back into an autonomous repair loop.

## Closed-Loop Engineering

ForgeLoop is built around closed-loop execution.

Depending on the repository and task, verification can include:

* compilation and build checks
* unit tests
* integration tests
* frontend tests
* static analysis and type checking
* GraphQL contract validation
* container health checks
* Playwright browser tests
* authorization and security checks
* architecture rules
* acceptance-criteria validation

A failed check becomes another input to the system.

```text
Implement
    │
    ▼
Verify
    │
    ├──── PASS ────► Continue
    │
    └──── FAIL
            │
            ▼
      Collect Evidence
            │
            ▼
        Diagnose
            │
            ▼
         Repair
            │
            └────────► Verify Again
```

Repair loops have explicit attempt budgets. ForgeLoop stops and requests human intervention when the harness can no longer establish a reliable path forward.

Autonomy has boundaries.

## Architecture

ForgeLoop uses a distributed control-plane and runner architecture.

```text
                         ForgeLoop Cloud
                ┌────────────────────────────┐
                │                            │
                │      React Dashboard       │
                │             │              │
                │             ▼              │
                │      Spring Boot API       │
                │             │              │
                │    ┌────────┼────────┐     │
                │    ▼        ▼        ▼     │
                │ Postgres   Queue   Storage │
                │                            │
                └─────────────┬──────────────┘
                              │
                        Task Dispatch
                              │
             ┌────────────────┼────────────────┐
             ▼                ▼                ▼
          Runner A         Runner B         Runner C
             │                │                │
       Git / Docker      Git / Docker      Git / Docker
       Tests / MCP       Tests / MCP       Tests / MCP
       Playwright        Playwright        Playwright
             │                │                │
             ▼                ▼                ▼
        Model APIs       Model APIs       Local Models
```

The web application acts as the control plane.

It manages:

* organizations and users
* repositories
* feature specifications
* runs and task graphs
* agents and harnesses
* runner registration
* model configuration
* MCP configuration
* project and organization rules
* events and logs
* verification results
* evidence and artifacts
* audit trails
* execution metrics

Runners perform the expensive and security-sensitive work close to the source repository.

## Self-Hosted Runners

ForgeLoop does not require arbitrary customer code to execute on the control-plane servers.

A runner can execute on a developer workstation, dedicated server, CI host, or infrastructure controlled by an organization.

Runners are responsible for operations such as:

```text
Git operations
Git worktrees
Agent execution
Filesystem access
MCP tools
Docker
Builds
Tests
Playwright
Local processes
Artifact collection
Model requests
```

This separates orchestration from execution and allows ForgeLoop to scale without centralizing every build, browser session, container, or agent process.

It also allows source code and provider credentials to remain within infrastructure controlled by the user.

## Parallel Agent Execution

ForgeLoop delegates bounded units of work rather than running one agent through an entire feature sequentially.

A task graph might look like:

```text
FEATURE-142

├── GraphQL schema
│
├── Backend assignment service
│   └── depends on: GraphQL schema
│
├── Authorization
│   └── depends on: Backend assignment service
│
├── React assignment UI
│   └── depends on: GraphQL schema
│
├── Backend tests
│   └── depends on: Backend + Authorization
│
├── Frontend tests
│   └── depends on: React assignment UI
│
└── Browser verification
    └── depends on: Integration
```

Tasks without dependencies can execute concurrently.

Each implementation agent receives only the context and tools needed for its assigned work.

## Isolated Workspaces

Parallel agents operate in isolated Git worktrees.

```text
repository/

worktrees/
├── feature-142-backend/
├── feature-142-frontend/
└── feature-142-tests/
```

Agents can modify and test their work independently without sharing a mutable working directory.

Completed changes are integrated before full-system verification begins.

Integration failures and merge conflicts can themselves become structured tasks handled by the harness.

## MCP

ForgeLoop uses the Model Context Protocol as a tool and context layer.

Local capabilities can include:

```text
repository.search
repository.get_context

git.status
git.diff

build.run

test.backend
test.frontend
test.integration

docker.start
docker.logs
docker.health

browser.navigate
browser.screenshot
browser.run_tests

verification.get_failures
verification.submit
```

Repository-sensitive MCP tools execute on the runner rather than the ForgeLoop control plane.

ForgeLoop can also connect agents to remote MCP services for systems such as source control, issue tracking, observability, and documentation.

MCP access is permissioned per agent and per harness.

## Tool Permissions

Agents do not automatically receive unrestricted access to the execution environment.

A frontend agent might be configured with:

```yaml
agent: frontend

tools:
  - repository.read
  - repository.write_frontend
  - test.frontend
  - browser.run

denied:
  - secrets.read
  - database.production
  - docker.privileged
```

Tool calls are recorded as part of the run's audit trail.

The goal is to provide enough autonomy to complete the assigned unit of work without giving every agent unrestricted control of the environment.

## Model Providers

ForgeLoop is designed around a provider abstraction rather than a single model vendor.

A harness can assign different models to different roles:

```yaml
agents:
  planner:
    provider: configured-provider
    model: configured-model

  backend:
    provider: configured-provider
    model: configured-model

  frontend:
    provider: configured-provider
    model: configured-model

  reviewer:
    provider: configured-provider
    model: configured-model
```

This makes model selection part of the harness rather than application architecture.

Provider support is designed to include hosted APIs, OpenAI-compatible endpoints, and local inference.

## Bring Your Own Key

ForgeLoop supports runner-managed provider credentials.

```text
ForgeLoop Control Plane
          │
          │ Execute TASK-829
          ▼
    ForgeLoop Runner
          │
          ├────────► Model Provider A
          ├────────► Model Provider B
          └────────► Local Model
```

API credentials can remain on the runner and do not need to pass through the ForgeLoop control plane.

This provides a straightforward model for individual developers and organizations that already maintain their own provider accounts.

## Independent Verification

Implementation and verification are intentionally separate concerns.

Where possible, verification agents derive tests and checks from the original feature specification rather than simply accepting tests written by the implementation agent.

For example:

```text
                 Feature Specification
                    │             │
                    ▼             ▼
             Implementation   Verification
                 Agent           Agent
                    │             │
                    ▼             ▼
                 Patch       Independent Tests
                    │             │
                    └──────┬──────┘
                           ▼
                        Execute
```

This reduces the chance that an agent's incorrect interpretation of a requirement is reinforced by tests based on the same interpretation.

## Browser Verification

Frontend work can be verified against a running application using Playwright.

A browser verification flow might:

```text
Start application
      │
      ▼
Wait for health checks
      │
      ▼
Authenticate
      │
      ▼
Navigate to feature
      │
      ▼
Perform interaction
      │
      ▼
Verify resulting UI
      │
      ▼
Reload
      │
      ▼
Verify persisted state
      │
      ▼
Exercise failure/authorization cases
```

Screenshots, browser results, logs, and failures become part of the run evidence.

## Evidence

Every run produces evidence describing what happened and why ForgeLoop considers the result ready for review.

```text
FEATURE-142/
├── specification.json
├── plan/
│   └── task-graph.json
├── agents/
│   ├── planner.json
│   ├── backend.json
│   ├── frontend.json
│   └── verification.json
├── diffs/
│   ├── backend.patch
│   └── frontend.patch
├── verification/
│   ├── compilation.txt
│   ├── backend-tests.xml
│   ├── frontend-tests.json
│   ├── integration-tests.xml
│   ├── playwright.json
│   └── container-health.json
├── screenshots/
├── review/
│   ├── requirements.json
│   └── security.json
├── metrics/
│   ├── tokens.json
│   ├── cost.json
│   └── timing.json
└── final-report.md
```

Instead of ending with "the agent says it works," ForgeLoop can show the evidence used to reach that state.

## Acceptance Criteria

Before a run can become ready for review, ForgeLoop evaluates the final implementation against its original requirements.

```text
Ticket assignment mutation               PASS
Organization membership validation       PASS
Authorization enforcement                PASS
Cross-organization rejection             PASS
React assignment interface               PASS
Audit event                               PASS
Backend tests                             PASS
Frontend tests                            PASS
Browser verification                     PASS
```

A successful run becomes:

```text
READY FOR HUMAN REVIEW
```

ForgeLoop does not need to automatically merge autonomous changes to provide autonomous engineering.

Humans retain the final merge boundary.

## Reusable Harnesses

A harness describes how ForgeLoop handles a class of engineering work.

### Full-Stack Feature

```text
Plan
 │
 ├──── Backend
 ├──── Frontend
 └──── Tests
          │
          ▼
       Integrate
          │
          ▼
         Build
          │
          ▼
         Test
          │
          ▼
        Browser
          │
          ▼
         Review
```

### Bug Fix

```text
Reproduce
    │
    ▼
Create Failing Test
    │
    ▼
Diagnose
    │
    ▼
Implement
    │
    ▼
Verify Reproduction
    │
    ▼
Regression Suite
```

The objective is for repeated classes of work to require less manual orchestration as the harness improves.

## Engineering Rules

Repositories and organizations can provide reusable rules that become part of agent context.

Examples:

```text
All GraphQL mutations require authorization.

Every API change requires integration coverage.

React mutations require loading, success, and error states.

Released database migrations are immutable.

All user-facing UI changes require browser verification.
```

This allows engineering knowledge to become part of the execution system rather than being repeatedly communicated to individual agents.

## Observability

ForgeLoop records execution metrics including:

* task duration
* queue time
* model and provider
* input/output tokens
* model cost
* tool calls
* verification attempts
* repair attempts
* failures
* human interventions
* acceptance-criteria coverage
* final run status

These metrics make it possible to evaluate agent configurations empirically.

Instead of assuming one model or harness is better, the same workload can be executed across configurations and compared using actual outcomes.

## Security

Autonomous execution requires explicit trust boundaries.

ForgeLoop is designed around:

* isolated workspaces
* scoped tool permissions
* runner-local secrets
* auditable tool calls
* configurable approval gates
* execution timeouts
* repair budgets
* process resource limits
* repository boundaries
* configurable network access
* human-controlled merge boundaries

The runner architecture also allows organizations to keep repository execution within infrastructure they control.

## Tech Stack

### Control Plane

* Java
* Spring Boot
* Spring Security
* GraphQL
* PostgreSQL
* Redis
* Docker

### Web

* React
* TypeScript
* GraphQL
* Playwright

### Runner

* Java
* Git
* Docker
* MCP
* Playwright
* local process execution

### AI

* provider-agnostic model interface
* hosted model APIs
* OpenAI-compatible endpoints
* local inference
* structured tool calling
* MCP

## Project Status

ForgeLoop is under active development.

The initial milestone focuses on one complete vertical slice:

```text
Feature Specification
        ↓
Repository Analysis
        ↓
Task Planning
        ↓
Parallel Agents
        ↓
Isolated Worktrees
        ↓
Integration
        ↓
Build + Tests
        ↓
Container Execution
        ↓
Browser Verification
        ↓
Autonomous Repair
        ↓
Independent Review
        ↓
Evidence Report
        ↓
Pull Request
```

The priority is reliable closed-loop execution rather than maximizing the number of agents, providers, integrations, or tools.

## Roadmap

### Phase 1: Control Plane + Runner

* [x] Spring Boot control plane
* [x] React dashboard
* [x] PostgreSQL persistence
* [x] authentication and organizations
* [x] repository registration
* [x] runner registration
* [x] task dispatch
* [x] live runner events

### Phase 2: Agent Execution

* [x] repository context builder
* [x] provider abstraction
* [x] structured agent tasks
* [x] Git worktree isolation
* [x] filesystem tools
* [x] build/test execution
* [x] execution artifacts

### Phase 3: Closed Loops

* [x] verification gates
* [x] structured failure evidence
* [x] autonomous repair
* [x] retry budgets
* [x] human escalation
* [x] evidence bundles

### Phase 4: Multi-Agent Orchestration

* [x] planner agent
* [x] task DAG
* [x] dependency scheduling
* [x] parallel workers
* [x] isolated agent workspaces
* [x] integration stage
* [x] conflict handling

### Phase 5: Full-System Verification

* [x] Docker Compose execution
* [x] service health checks
* [x] Playwright
* [ ] screenshots
* [x] independent verification agent
* [x] acceptance-criteria review

### Phase 6: Harness Platform

* [x] reusable harness definitions
* [x] organization execution policies
* [x] runner-local MCP configuration and task context routing
* [x] per-agent tool permissions
* [x] multiple model providers
* [x] runner-managed BYOK
* [x] run analytics
* [x] model/harness comparisons

## What ForgeLoop Is Not

ForgeLoop is not intended to be another chat interface around an LLM.

It is not built around continuously asking a developer what an agent should do next.

The project explores a different question:

**What infrastructure does an engineering agent need to own a bounded unit of software work, operate autonomously, detect when it is wrong, repair its work, and provide objective evidence that the result satisfies the original specification?**

That infrastructure is ForgeLoop.

## License

License information will be added as the project matures.
