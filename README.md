# ForgeLoop

**A live software-delivery product—and a case study in making coding agents accountable for verified work.**

[Explore the hosted product](https://forgeloop.codefrontlabs.com) · [View the source](https://github.com/JRH89/ForgeLoop)

ForgeLoop takes GitHub issues or feature specifications through planning, bounded agent work, integration, verification, and human review. Its central design principle is simple: an agent saying “done” is not evidence. A run becomes reviewable only when its configured checks and acceptance criteria have supporting results.

> This repository is presented as a technical portfolio case study, not a self-hosting or local-install guide. The hosted site is the product demo; GitHub sign-in is required to use the console. The runner is a separate execution component in the product architecture.

## The engineering problem

Coding agents can produce patches, but reliable software delivery also needs safe coordination, repository access, isolated execution, bounded spend, recovery from failure, and proof that a change meets its requirements. ForgeLoop explores how to make those concerns explicit in a working product rather than hiding them behind a chat interface.

### The delivery loop

1. A GitHub issue or operator specification enters through repository-specific intake rules.
2. A planner turns the request into a validated dependency graph of bounded tasks.
3. Runners claim compatible work and execute it in isolated Git worktrees, using locally configured model credentials.
4. Changes are integrated, then repository-policy checks, acceptance criteria, and an independent review produce evidence.
5. Failed checks can route into a limited repair cycle. Passing work follows the configured human-approval policy before GitHub publication; auto-merge is opt-in and checks the verified commit.

Administrators can also manually ask a runner to scan a repository snapshot for evidence-backed issue proposals. Scans are read-only, may incur provider charges, and never create GitHub issues without an explicit review action.

## Architecture

| Component | Responsibility |
| --- | --- |
| Hosted web app | Public product pages and the GitHub-authenticated operator console for runs, repositories, policies, usage, support, and evidence. |
| Control plane | Java and Spring Boot GraphQL API; organization-scoped state, policy, issue intake, task scheduling, runner leases, audit records, and GitHub coordination. |
| PostgreSQL and artifact storage | Flyway-managed durable workflow state plus redacted, checksummed evidence in filesystem or S3-compatible storage. |
| ForgeLoop runner | Java desktop/CLI process near the repository; uses Git worktrees and Docker for execution, MCP for approved context/tools, and runner-local provider credentials. |
| External services | GitHub App and OAuth for identity/repository events; configured hosted or local model providers for agent work. |

The control plane coordinates work; the runner handles checkout, execution, and model calls. The hosted service stores workflow records, issue text, findings, usage, and verification evidence. Optional detailed run records can also upload prompts, provider responses, and repository context as artifacts. Local execution is not an offline-only or zero-data-transfer promise; provider data handling still applies.

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

A live issue-to-verified-PR path has been demonstrated against a demo repository. A second-repository unattended issue-to-merge run was also validated on October 9, 2026. Remaining checks include off-host backup/restore, a physical reboot/server validation, staging burn-in, and a production go/no-go review. Desktop installers are unsigned development previews and macOS packages are not notarized. The website and desktop downloads label this limitation.

This is an evolving portfolio product, not a claim that every production launch gate is closed. The open items are listed at the bottom so implementation and operational evidence remain distinct.

## Further reading

- [Reliability and recovery](docs/reliability.md)
- [Linux server deployment and Cloudflare Tunnel](docs/cloudflare-tunnel.md)
- [Gitea webhook deployment](docs/cicd-deployment.md)
- [Deferred launch checks](docs/deferred-launch-checks.md)
- [Desktop release workflow](docs/desktop-releases.md)
- [Public website verification](docs/public-website.md)
- [Runner user guide](frontend/src/docs/runner-setup.md)

## Delivery checklist

Checklist reviewed October 15, 2026. Checked items are implemented or have the
specific evidence stated; they do not imply every production validation is complete.

### Product and control plane

- [x] GitHub sign-in and self-service account provisioning with organization-scoped membership and roles.
- [x] GitHub App installation, signed webhook validation, idempotent deliveries, repository synchronization, and configurable issue-label intake.
- [x] Optional per-repository label bypass and Issue Chat label application with explicit paid-work warnings.
- [x] Optional issue-assignment gate for any assignee or a specified GitHub login; read-only intake diagnostics.
- [x] Manual repository analysis proposals with bounded/redacted context, runner-local model calls, usage estimates, and administrator-approved issue creation.
- [x] AI issue chat with editable specifications, explicit issue publication, optional intake-label application, and conversation deletion.
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
- [x] Independent review and criterion-level evidence before delivery, with human approval required only when configured.
- [x] GitHub check and pull-request publication; guarded auto-merge is opt-in and validates checks and the expected commit.

### Product experience and engineering quality

- [x] Responsive dashboard and public product pages, user documentation, SEO/social metadata, and structured data.
- [x] Cross-platform desktop preview packaging and GitHub Release download discovery with SHA-256 verification.
- [x] Runner 1.0.12 preview published for Windows/macOS x64 and ARM64, plus Linux DEB, RPM, Arch, and portable archives for both architectures; all twelve package digests verified.
- [x] Navy-and-teal desktop redesign, rounded native icons, HiDPI branding, and local preview/window-control regression coverage.
- [x] Consolidated Settings, persistent repository expansion/banner preferences, concise download guidance, and OS-specific download selectors.
- [x] Automated CI for Java modules, frontend checks, MCP checks, Windows setup, and disposable end-to-end workflows.
- [x] Live public downloads/release-feed smoke check for 1.0.12, separate from installer signing.
- [x] Linux server Compose deployment with loopback diagnostic ports and server-hosted Cloudflare ingress; physical reboot recovery remains unverified.

### Live validation and remaining launch gates

- [x] Complete a live verified delivery and merge test: project-hub issue #6 / PR #7 merged after manual approval ([October 9 evidence](docs/second-repository-validation-2026-10-09.md#completion-and-unattended-follow-up)).
- [x] Complete a second-repository unattended issue-to-merge test: project-hub issue #10 / PR #11 merged automatically with human approval disabled ([successful validation](docs/second-repository-validation-2026-10-09.md#successful-unattended-validation)). Both delivery tests are complete; operational launch gates remain separate.
- [ ] Validate a physical workstation/server reboot, tunnel recovery, runner heartbeat, and authenticated access.
- [ ] Configure encrypted off-host backup retention and complete a restore drill on another host, including evidence objects.
- [ ] Complete staging burn-in.
- [ ] Verify external alert delivery.
- [ ] Record an explicit production go/no-go review after the operational checks above.
- [ ] Sign Windows/macOS installers and notarize macOS packages; published previews remain unsigned.
- [x] Publish the canonical-domain SEO migration and new authorization article: October 15 live audit checked all 21 public pages with zero findings; old-host article redirects preserve path/query, and the new social image returns HTTP 200 ([audit evidence](docs/seo-audit-2026-10-15.md#deployment-verification)).
- [ ] Verify the new domain in Google Search Console and submit its canonical sitemap; search indexing and ranking are not established by build checks.
- [ ] Enable and verify support email notifications.

### October 22 repository hardening

Run history now uses tenant-scoped database pagination, with direct issue-source
lookups and complete paginated usage reporting. The guarded multi-turn writing
worker is connected behind a default-off runner switch and repository policy
opt-in. Lease renewal, spend reservations, cancellation, and verification
boundaries are described in [agent-loop activation](docs/agent-loop.md).
Security updates, immutable CI action pins, code separation, bounded load
scenarios, and local validation are recorded in the
[hardening report](docs/hardening-2026-10-22.md). Operational launch gates remain
separate and unchanged.
