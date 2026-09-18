# Operations guide

## Environments

`demo` is the only profile that accepts `X-Actor-Id` and seeds data. It is for local demonstrations only. Docker Compose explicitly selects it.

The default profile is production-oriented: GraphiQL is disabled, Hibernate validates rather than creates schema, Flyway applies versioned migrations, no datasource credentials have defaults, and demo seeding/header identity are disabled. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` through a secrets manager before startup. A production deployment must provide an OIDC/JWT principal adapter before accepting traffic; the application deliberately does not fall back to the header transport.

## Release gates

1. Run backend and harness Maven verification, frontend and MCP checks, Compose health, and Playwright.
2. Scan the built images and lockfiles in CI; block known critical vulnerabilities.
3. Run Flyway against a disposable PostgreSQL instance, then a reviewed production migration window.
4. Configure OIDC issuer/audience, secret injection, HTTPS termination, backups, database least-privilege roles, log retention, and alert routing.
5. Protect `main`, require CI, code review, and signed release artifacts before deployment.

## Incident handling

Use `/actuator/health` for load-balancer readiness. Preserve the feature evidence bundle and immutable audit events during an incident. Disable worker execution rather than bypassing a failed verification gate; a blocked run is safer than an unverified change.
