# Repository hardening — October 22, 2026

This work follows the original outline's bounded execution, tenant isolation,
closed-loop verification, and evidence requirements. Deployment-dependent
backup, reboot, staging, alert, signing, and SMTP checks remain deferred at the
user's direction; no production readiness gate is marked complete here.

## Changes

- Run history is tenant-filtered and connection-filtered in SQL, with stable
  ordering, validated page bounds, an organization/history index, archive
  selection, and browser pagination. Database failures propagate instead of
  silently hiding records. Single-run access checks the run's organization
  before loading its graph. Source lookups prevent issue delivery tracking from
  depending on the first page; usage reports retrieve and deduplicate pages.
- The guarded agent loop is connected to writing-task dispatch with two opt-ins:
  the runner-local default-off switch and the repository policy snapshot.
  Reviewed provider policy must allow tool calling. The adapter validates and
  renews leases, reports per-turn usage, reserves known spend, maps policy and
  budget stops to holds, and revalidates before returning commits for delivery.
  Official verification remains a separate task. Lease loss stops publication.
- Run list, creation, detail, and presentation helpers are separated from the
  application entry point. Provider diagnostics and standalone verification CLI
  handlers are separated from runner orchestration.
- GitHub Actions use immutable commit pins. Security scans block high and
  critical findings, including unfixed findings, and weekly dependency update
  configuration covers all package ecosystems. npm security updates retain
  explicit versions and reproducible lockfiles.
- The read-only load harness adds bounded full-response timeouts, history,
  analytics, detail and mixed scenarios, capped latency sampling, and explicit
  failure handling. CI exercises HTTP failure fixtures without external services.

## Verification boundaries

Automated tests cover SQL tenant/connection filtering, archive/page boundaries,
source ownership, UI page navigation, full usage history, dispatch opt-ins,
successful loop commits, cancellation/lease loss, renewal, usage reporting,
lifecycle HTTP requests, malformed policy/acknowledgement rejection, and
load-harness errors/timeouts. Existing suites cover shared reservation,
enforcement, bounded repairs, and official verification gates.

Local execution results:

- Control plane: full Maven verification, 377 tests, no failures/errors/skips.
- Runner: full Maven verification, 391 tests, no failures/errors and six existing
  platform/live-fixture skips. After the final mount-path correction and new spend
  refusal test, 16 focused dispatch/container/CLI tests passed; the combined report
  contains 392 tests. The final runner package build also passed.
- Harness: Maven verification, two tests passed.
- Frontend: lint, 100 tests across 25 files, type checking, production build,
  prerendering, and SEO validation for all 21 public pages passed.
- MCP: type checking and four tests passed.
- Load harness: five localhost HTTP fixture tests passed.
- Both npm dependency update/audit runs reported zero vulnerabilities.
- Workflow/Dependabot YAML parsing and `git diff --check` passed.

Live provider and Docker-backed loop execution, release image scans, physical
reboot, and external delivery are not established by these offline checks.

## Release scan follow-up

The first hosted HIGH/CRITICAL scan exposed pre-existing Jackson findings and
root Nginx master processes in both web Dockerfiles. The first 1.0.13 packaging
run was cancelled before publication. Runner Jackson was updated to 2.21.7; the
Spring Boot modules pin the Jackson 2 and 3 BOMs to 2.21.7 and 3.1.7. Both web
images retain their HTTP port and use the nginx account, an owner-writable cache,
and a PID file under `/tmp`. Local Java verification and hosted security and
release checks are rerun against these corrections before publishing.
