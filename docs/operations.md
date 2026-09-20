# Operations guide

## Environments

Docker Compose explicitly sets `FORGELOOP_SECURITY_MODE=development` for local evaluation only. It deliberately does not represent a production deployment.

The default security mode is `production`: all operator routes require a JWT validated by Spring Security's OIDC resource server. Set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` (or a JWK set URI) through a secrets manager before startup. Only `/actuator/health` and signed GitHub webhooks bypass JWT authentication; webhook authenticity is independently checked with the configured HMAC secret.

## Release gates

1. Run backend and harness Maven verification, frontend and MCP checks, Compose health, and Playwright.
2. Scan the built images and lockfiles in CI; block known critical vulnerabilities.
3. Run Flyway against a disposable PostgreSQL instance, then a reviewed production migration window.
4. Configure OIDC issuer/audience, secret injection, HTTPS termination, backups, database least-privilege roles, log retention, and alert routing.
5. Protect `main`, require CI, code review, and signed release artifacts before deployment.

## Incident handling

Use `/actuator/health` for load-balancer readiness. Preserve the feature evidence bundle and immutable audit events during an incident. Disable worker execution rather than bypassing a failed verification gate; a blocked run is safer than an unverified change.
