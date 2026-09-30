# ForgeLoop

**A live software-delivery product—and a case study in making coding agents accountable for verified work.**

[Explore the hosted product](https://forgeloop.hookerhillstudios.com) · [View the source](https://github.com/JRH89/ForgeLoop)

ForgeLoop takes GitHub issues or feature specifications through planning, bounded agent work, integration, verification, and human review. Its central design principle is simple: an agent saying “done” is not evidence. A run becomes reviewable only when its configured checks and acceptance criteria have supporting results.

> This repository is presented as a technical portfolio case study, not a self-hosting or local-install guide. The hosted site is the product demo; GitHub sign-in is required to use the console. The runner is a separate execution component in the product architecture.

## The engineering problem

Coding agents can produce patches, but reliable software delivery also needs safe coordination, repository access, isolated execution, bounded spend, recovery from failure, and proof that a change meets its requirements. ForgeLoop explores how to make those concerns explicit in a working product rather than hiding them behind a chat interface.

### The delivery loop

1. A GitHub issue or operator specification enters through repository-specific intake rules.
2. A planner turns the request into a validated dependency graph of bounded tasks.
3. Runners claim compatible work and execute it in isolated Git worktrees, using locally configured model credentials.
4. Changes are integrated, then repository-policy checks, acceptance criteria, and an independent review produce evidence.
5. Failed checks can route into a limited repair cycle. Passing work reaches a human approval boundary before GitHub publication; auto-merge is opt-in and checks the reviewed commit.

Administrators can also manually ask a runner to scan a repository snapshot for evidence-backed issue proposals. Scans are read-only, may incur provider charges, and never create GitHub issues without an explicit review action.

## Architecture

| Component | Responsibility |
| --- | --- |
| Hosted web app | Public product pages and the GitHub-authenticated operator console for runs, repositories, policies, usage, support, and evidence. |
| Control plane | Java and Spring Boot GraphQL API; organization-scoped state, policy, issue intake, task scheduling, runner leases, audit records, and GitHub coordination. |
| PostgreSQL and artifact storage | Flyway-managed durable workflow state plus redacted, checksummed evidence in filesystem or S3-compatible storage. |
| ForgeLoop runner | Java desktop/CLI process near the repository; uses Git worktrees and Docker for execution, MCP for approved context/tools, and runner-local provider credentials. |
| External services | GitHub App and OAuth for identity/repository events; configured hosted or local model providers for agent work. |

The control plane coordinates work; the runner handles repository checkout and execution. Repository context goes from the runner to the configured model provider, not through the control-plane database. The service stores workflow records, findings, usage, and evidence metadata. Provider data handling still applies to context sent to a selected model.

## Engineering decisions

| Challenge | Design response |
| --- | --- |
| Parallel agents can overwrite or depend on one another’s changes. | Validate task graphs, capabilities, dependencies, and owned paths; issue expiring single-owner leases; use isolated worktrees; integrate declared commits deterministically. |
| Model output can be incomplete, malformed, or overconfident. | Require structured outputs, validate paths and schemas, run versioned verification gates, track criterion-level evidence, and use bounded repair and review stages. |
| Repository execution and credentials are sensitive. | Keep checkout/provider work on the runner; use short-lived GitHub installation grants, hashed runner credentials, organization membership checks, redaction, and audited operator actions. |
| Services and workers can fail mid-run. | Persist workflow transitions, publish runner heartbeats, expire and recover leases, retain checksummed artifacts, and expose progress and failure evidence to operators. |
| Agent usage has real cost and needs a human boundary. | Track provider/model/token and estimated-cost data, enforce budgets and retry limits, require configured approval, and keep automatic merge disabled unless an administrator opts in. |

## Technology

- **Backend:** Java 21, Spring Boot, Spring Security, GraphQL, Spring Data JPA, Flyway, PostgreSQL.
- **Web:** React 19, TypeScript, Vite, Vitest, Playwright; the public site is prerendered with SEO and social metadata.
- **Runner:** Java 21, Git, Docker, MCP, local provider adapters, and native desktop packaging for Windows, macOS, and Linux previews.
- **Tool gateway:** TypeScript, the Model Context Protocol SDK, and Zod schemas.
- **Delivery and operations:** GitHub Apps and OAuth, GitHub Actions, Docker Compose, filesystem/S3-compatible artifact storage.

## Evidence and project boundaries

The hosted product is live and the repository contains the implementation and automated checks. Current CI covers the control plane, runner, web app, MCP gateway, Windows installer setup, and a disposable Compose end-to-end environment that exercises database restore, runner pairing, package enrollment, and browser tests.

A live issue-to-verified-PR path has been demonstrated against a demo repository. Validation across a second unrelated repository remains open; so do off-host backup/restore, a physical reboot/server validation, staging burn-in, and a production go/no-go review. Desktop installers are unsigned development previews and macOS packages are not notarized. The website and desktop downloads label this limitation.

This is an evolving portfolio product, not a claim that every production launch gate is closed. The open items are listed at the bottom so implementation and operational evidence remain distinct.

## Further reading

- [Reliability and recovery](docs/reliability.md)
- [Linux server deployment and Cloudflare Tunnel](docs/cloudflare-tunnel.md)
- [Deferred launch checks](docs/deferred-launch-checks.md)
- [Desktop release workflow](docs/desktop-releases.md)
- [Public website verification](docs/public-website.md)
- [Runner user guide](frontend/src/docs/runner-setup.md)

## Delivery checklist

### Product and control plane

- [x] GitHub sign-in and self-service account provisioning with organization-scoped membership and roles.
- [x] GitHub App installation, signed webhook validation, idempotent deliveries, repository synchronization, and configurable issue-label intake.
- [x] Optional issue-assignment gate for any assignee or a specified GitHub login; read-only intake diagnostics.
- [x] Manual repository analysis proposals with bounded/redacted context, runner-local model calls, usage estimates, and administrator-approved issue creation.
- [x] Persisted runs, task graphs, gates, acceptance criteria, audit history, approval, cancellation, bounded retries, and queue archive/restore.
- [x] Organization policies for budgets, concurrency, allowed providers, human approval, and optional verified-check auto-merge.
- [x] Usage views for model/provider spend estimates, tokens, run costs, pricing coverage, and comparisons.
- [x] Customer support intake and tracking for signed-in and guest users, plus an administrator support inbox.

### Runner, execution, and evidence

- [x] Runner enrollment and browser pairing with one-time tokens, hashed credentials, heartbeats, capability matching, and pause/recovery behavior.
- [x] Runner-local provider configuration and secret storage; adapters for Anthropic, OpenAI-compatible, Gemini, and local models.
- [x] Lease-bound GitHub checkout, isolated worktrees, dependency-aware parallel execution, deterministic integration, and bounded repair.
- [x] Docker verification with pinned images, time/output limits, and policy-controlled network access.
- [x] Permissioned MCP context/tool routing with runner-side command controls.
- [x] Redacted lifecycle logs, provider usage records, checksummed verification evidence, screenshots, and filesystem/S3-compatible artifact storage.
- [x] Independent review and criterion-level evidence before a run reaches human review.
- [x] GitHub check and pull-request publication; guarded auto-merge is opt-in and validates checks and the expected commit.

### Product experience and engineering quality

- [x] Responsive dashboard and public product pages, user documentation, SEO/social metadata, and structured data.
- [x] Cross-platform desktop preview packaging and GitHub Release download discovery with SHA-256 verification.
- [x] Automated CI for Java modules, frontend checks, MCP checks, Windows setup, and disposable end-to-end workflows.

### Remaining validation

- [ ] Complete an end-to-end run in a second unrelated repository profile after provider funding is available.
- [ ] Validate a physical workstation/server reboot, tunnel recovery, runner heartbeat, and authenticated access.
- [ ] Configure encrypted off-host backup retention and complete a restore drill on another host, including evidence objects.
- [ ] Complete staging burn-in, external alert delivery, and an explicit production go/no-go review.
- [ ] Sign Windows/macOS installers, notarize macOS packages, and verify published release assets in a public smoke test.
- [ ] Enable and verify support email notifications.
