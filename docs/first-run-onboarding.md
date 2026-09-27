# First-run onboarding

## Completed scope

- Five-step setup checklist, available from Runs and the sidebar after User guide.
- Repository selection and ten-second, non-overlapping metadata polling with
  explicit stale/error states and manual refresh.
- Saved harness, intake label, assignee, budget, approval, auto-merge, and gate visibility.
- Desktop download and local-only key/connection-check guidance.
- GitHub issue draft template without automatic labels or assignments.
- Read-only, tenant-scoped `issueIntakeCheck(repository, issueNumber)` GraphQL query.
  Ownership is checked before GitHub access. The existing App adapter reads the
  issue with an installation token; errors never expose issue bodies or tokens.
- Shared eligibility rules between webhook intake and setup diagnostics: exact
  label, configured case-insensitive assignee, nonblank body, not closed or a PR.
- Waiting-run explanations tied to persisted facts, not a fabricated readiness score.

## Boundaries

No migrations, provider calls, issue creation, webhook replay, task claims, or
runner restarts are part of readiness checks. The GitHub read may mint a
short-lived installation token. Each upstream HTTP request has a 15-second
timeout and existing bounded transient retries. Repository connection presence
is not live proof of all App permissions. Eligibility is not delivery proof.

Keys, balances, local tools, workspace builds, and provider readiness cannot be
verified remotely. Recent heartbeat means contact within 60 seconds; long task
batches may delay heartbeats. Failure explanations link back to the existing run
evidence rather than claiming to reconstruct every scheduling decision.

No auto-redirect replaces the familiar Runs page. Viewers can inspect readiness
but cannot see the install action; all existing server authorization remains in
force for mutations accessed through other pages.

## Verification

- Backend tests: eligible issue, missing label/assignee/body, closed issue/PR,
  case-insensitive assignee, invalid number, malformed GitHub response, safe
  upstream error, and ownership checked before any external call.
- Frontend tests: fresh workspace, populated checklist, explicit issue check,
  viewer controls, failure/retry, stale heartbeat, configuration warnings,
  waiting explanations, and safe draft URL.
- Browser regression: setup entry, sidebar order, intake diagnosis, and mobile
  layout using deterministic API fixtures (not evidence of a live GitHub delivery).
- Run `npm run check` in `frontend` and `mvn verify` in `control-plane`.

Local verification on 2026-09-27: backend Docker/Maven verification passed;
frontend lint, typecheck, production build and 18-page SEO checks passed; 11
browser checks passed against an isolated PostgreSQL/control-plane/Nginx stack
(dashboard, onboarding at 390px/1440px, and public website). Spring reported no
unmapped GraphQL fields or arguments. The isolated stack had no provider keys
or runners. GitHub HTTP behavior was tested against a local server, not a paid
provider or a live issue mutation.

Funded end-to-end repository execution, signed installers, off-host backups, and
server/reboot validation remain separate release gates; see
[deferred launch checks](deferred-launch-checks.md).
