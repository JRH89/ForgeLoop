# Operations guide

## Environments

Compose mounts the named `forgeloop-postgres-data` volume at PostgreSQL 18's
`/var/lib/postgresql` directory. `FORGELOOP_POSTGRES_VOLUME` can select a recovered
volume. Container recreation retains this data; `docker compose down --volumes`
deletes managed volumes and must not be used on a deployment with valuable data.
Back up before changing volume selection. An older anonymous volume can be
recovered by copying it while its database is stopped, validating that copy in
an isolated PostgreSQL container, and selecting the recovered named volume.

On 2026-09-26, the workstation database was recovered from its retained anonymous
volume: 10 runs and two repository connections. The original volumes were retained.

Docker Compose defaults to `FORGELOOP_SECURITY_MODE=github`, which protects the browser console with GitHub OAuth and persisted organization membership. Use `development` only for isolated local tests; it permits operator routes without a user identity and must never be exposed through the tunnel.

The `production` security mode retains GitHub browser sessions and additionally accepts JWTs validated by Spring Security's OIDC resource server. Configure both `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` and `FORGELOOP_OIDC_AUDIENCE` through a secret manager before selecting it. The validated `org_id` claim must match a persisted organization membership; roles come only from that membership, never from caller-supplied GraphQL input. Health probes and signed GitHub webhooks bypass operator authentication because they have separate security boundaries.

## Production configuration boundary

The control plane fails before accepting traffic in production unless it has a PostgreSQL datasource, `SPRING_JPA_DDL_AUTO=validate`, OIDC issuer and audience, GitHub webhook secret, an `s3://bucket[/prefix]` artifact-storage URI, and a secrets-manager-provided encryption key of at least 32 characters. S3-compatible endpoints can be selected with `FORGELOOP_ARTIFACT_S3_ENDPOINT`; credentials must come from the standard AWS SDK credential chain and never from repository configuration.

Verification workers upload a bounded JSON bundle through their authenticated active lease. The control plane recomputes its SHA-256 checksum, stores it under a tenant/run/task/lease key, reads it back, verifies the persisted bytes, and only then writes immutable metadata. `FORGELOOP_ARTIFACT_RETENTION_DAYS` records the required retain-until date; configure an equivalent or longer bucket lifecycle/Object Lock policy because database metadata alone does not prevent an infrastructure administrator from deleting an object. Local Compose uses a named filesystem volume for development and is not an immutable production backend.

Bootstrap the first organization and administrator as a controlled deployment action. After bootstrap, only a persisted administrator may grant memberships. Do not add a broad unauthenticated bootstrap mutation.

Audit entries contain actor, action, resource identity, timestamp, and a SHA-256 digest of sensitive action material. They deliberately exclude specifications, prompts, credentials, source content, and raw verification output. Use the tenant-scoped `featureRunAuditEvents` GraphQL query to inspect a run's history.

## Release gates

1. Run backend and harness Maven verification, frontend and MCP checks, Compose health, and Playwright.
2. Scan the built images and lockfiles in CI; block known high and critical vulnerabilities.
3. Run Flyway against a disposable PostgreSQL instance, then a reviewed production migration window.
4. Configure OIDC issuer/audience, secret injection, HTTPS termination, backups, database least-privilege roles, log retention, and alert routing.
5. Protect `master`, require CI, code review, and signed release artifacts before deployment.

The `Supply-chain evidence` workflow scans dependencies, secrets, configuration, and all three release images for high and critical vulnerabilities, including findings without an available fix. It also publishes a commit-bound SPDX JSON SBOM for 90 days. Third-party actions are pinned to immutable commit SHAs; review and update those pins through a dependency-update pull request.

## Backup and restore drill

Managed PostgreSQL must have encrypted point-in-time recovery and cross-failure-domain snapshots. At least quarterly, restore a snapshot into an isolated account/project, run Flyway validation and application smoke tests, and record recovery-point and recovery-time evidence. A backup is not considered usable until a restore succeeds.

For a local, destructive-to-the-temporary-target-only rehearsal, run `./scripts/Test-PostgresRestore.ps1` while the Compose database is healthy. The script makes a custom-format dump, waits for the isolated PostgreSQL 18 container's final server (not its temporary initialization server), restores into tmpfs, validates the latest successful Flyway version and repository table, prints the result, then deletes the temporary container and dump. It never writes to the source database. Hosted production evidence is still required before launch.

## Service objectives and alerts

Initial pilot objectives are 99.9% monthly control-plane availability, 99% of valid signed webhook deliveries durably acknowledged within 10 seconds, and 99% of eligible task dispatches offered to a healthy matching runner within 30 seconds. Alert on readiness failure, webhook 5xx rate, lease-expiry/repair spikes, queue age, database saturation, artifact-write failure, and provider failure rate. Page only on user-impacting symptoms; route capacity and budget warnings to a non-paging channel.

Run `node scripts/load-test.mjs` against a development or staging control plane; never point it at production without an approved window. Configure `FORGELOOP_LOAD_URL`, `FORGELOOP_LOAD_TOKEN`, `FORGELOOP_LOAD_DURATION_MS`, and `FORGELOOP_LOAD_CONCURRENCY`. The read-only test fails when errors exceed 1% or p95 exceeds 500 ms and emits a compact JSON result suitable for release evidence. This local single-node test is a regression gate, not a substitute for a staged distributed capacity test.

## Incident handling

Use `/actuator/health/liveness` for process restarts and `/actuator/health/readiness` for load-balancer routing. Prometheus metrics are available at the authenticated `/actuator/prometheus` endpoint; do not expose it publicly. Preserve the feature evidence bundle and immutable audit events during an incident. Disable worker execution rather than bypassing a failed verification gate; a blocked run is safer than an unverified change.

Runner progress and automated intervention are documented in [runner-events-and-escalation.md](runner-events-and-escalation.md). Alert on open `HIGH` escalations and on escalations remaining unacknowledged beyond the response SLO. Acknowledging an escalation is not approval to retry or ship.

The application safety net permits 600 requests per minute per runner or network peer by default and returns `429` with `Retry-After`; configure `FORGELOOP_HTTP_REQUESTS_PER_MINUTE` for measured traffic. Health probes bypass this limiter. Production ingress must independently enforce connection, request-body, and distributed tenant/IP limits because an in-process limiter is neither a WAF nor a cross-replica quota system.

## Bounded run history and load scenarios

`featureRuns(page: 0, size: 50, archived: false)` filters the authenticated
organization and enabled repository connections in the database before loading
its display graph. Pages are ordered by creation time and ID, newest first.
Page indexes are 0–100000 and sizes are 1–100; invalid bounds are rejected.
Omitting `archived` includes both active and archived history. The Runs screen
uses 50-row pages, resets pagination when changing archive selection, and labels
its status filters as applying to the current page. Offset pages can move when
new runs arrive; usage history deduplicates IDs across pages and fails visibly
rather than returning a partial report beyond its 50,000-row safety ceiling.
Issue Chat uses the tenant-checked `featureRunForSource(repository, sourceRef)`
lookup so older issues do not disappear behind a page boundary. Aggregate
analytics retain the complete authorized history.

The load harness supports `FORGELOOP_LOAD_SCENARIO=history`, `analytics`,
`detail`, or `mixed`. Detail and mixed require `FORGELOOP_LOAD_RUN_ID` for an
existing authorized staging run. Every scenario is read-only. Set
`FORGELOOP_LOAD_REQUEST_TIMEOUT_MS` (100–60000, default 5000) to bound the entire
request, including response-body reading. A run takes at most the configured
traffic duration plus one request timeout, excluding process startup. HTTP
failures, GraphQL errors, missing data, redirects, and timeouts count as failed
requests. Output includes timeout count and latency sample size; percentiles use
a representative reservoir capped at 100,000 samples. The gate remains at most
1% failures and p95 at most 500 ms. `node --test scripts/load-test.test.mjs` runs
local HTTP failure/timeout fixtures; it does not establish staging capacity.

Dependency scans block HIGH and CRITICAL findings even when no fix is available.
Resolve or explicitly review a finding before release; do not weaken the scan
or silently ignore unfixed vulnerabilities. Weekly Dependabot updates cover
GitHub Actions, both npm packages, and all three Maven modules. Workflow action
pins are immutable commit SHAs, including the dereferenced CodeQL release tag.
