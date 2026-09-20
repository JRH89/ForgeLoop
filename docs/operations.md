# Operations guide

## Environments

Docker Compose explicitly sets `FORGELOOP_SECURITY_MODE=development` for local evaluation only. It deliberately does not represent a production deployment.

The default security mode is `production`: all operator routes require a JWT validated by Spring Security's OIDC resource server. Configure both `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` and `FORGELOOP_OIDC_AUDIENCE` through a secrets manager before startup. The validated `org_id` claim must match a persisted organization membership; roles come only from that membership, never from a caller-supplied GraphQL value. Only `/actuator/health` and signed GitHub webhooks bypass JWT authentication; webhook authenticity is independently checked with the configured HMAC secret.

## Production configuration boundary

The control plane fails before accepting traffic in production unless it has a PostgreSQL datasource, `SPRING_JPA_DDL_AUTO=validate`, OIDC issuer and audience, GitHub webhook secret, a supported object-storage URI (`s3://`, `gs://`, or `azure://`), and a secrets-manager-provided encryption key of at least 32 characters. The storage setting is validated now; immutable remote artifact persistence is introduced in Slice 3 and Slice 6.

Bootstrap the first organization and administrator as a controlled deployment action. After bootstrap, only a persisted administrator may grant memberships. Do not add a broad unauthenticated bootstrap mutation.

Audit entries contain actor, action, resource identity, timestamp, and a SHA-256 digest of sensitive action material. They deliberately exclude specifications, prompts, credentials, source content, and raw verification output. Use the tenant-scoped `featureRunAuditEvents` GraphQL query to inspect a run's history.

## Release gates

1. Run backend and harness Maven verification, frontend and MCP checks, Compose health, and Playwright.
2. Scan the built images and lockfiles in CI; block known critical vulnerabilities.
3. Run Flyway against a disposable PostgreSQL instance, then a reviewed production migration window.
4. Configure OIDC issuer/audience, secret injection, HTTPS termination, backups, database least-privilege roles, log retention, and alert routing.
5. Protect `main`, require CI, code review, and signed release artifacts before deployment.

## Incident handling

Use `/actuator/health` for load-balancer readiness. Preserve the feature evidence bundle and immutable audit events during an incident. Disable worker execution rather than bypassing a failed verification gate; a blocked run is safer than an unverified change.
