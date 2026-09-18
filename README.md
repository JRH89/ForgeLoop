# ForgeLoop

ForgeLoop is an evidence-first, multi-agent software-delivery harness. It turns a bounded feature specification into a reviewable change only after objective verification gates pass. The included `FEATURE-142` vertical slice is a B2B support desk: organization administrators may assign a ticket only to a member of their own organization.

## What is real today

* A Java 21 / Spring Boot 4 GraphQL API with PostgreSQL persistence, explicit authorization, validation, and immutable audit events.
* A React 19 dashboard that uses an optimistic assignment update and reconciles with the GraphQL response.
* Docker Compose starts the API, UI, and PostgreSQL with health checks.
* The harness models planning, parallel execution, verification, bounded repair, review, and evidence output. Its worker boundary is provider-agnostic and records selection rationale.
* A TypeScript MCP stdio server exposes repository, feature, and verification tools to a model host. Destructive operations are deliberately not exposed.

## Quick start

Prerequisites: Docker Desktop (or Java 21+, Maven 3.9+, Node 22+).

```sh
docker compose up --build
```

Open `http://localhost:5173`; the GraphQL endpoint is `http://localhost:8080/graphql`, and health is `http://localhost:8080/actuator/health`.

For local development:

```sh
cd backend && mvn verify
cd ../frontend && npm ci && npm run check
cd ../mcp-server && npm ci && npm test
```

## Architecture and safety

See [docs/architecture.md](docs/architecture.md), [docs/verification.md](docs/verification.md), [docs/feature-142.md](docs/feature-142.md), and [CONTRIBUTING.md](CONTRIBUTING.md). `X-Actor-Id` is a demo identity transport only; production must replace it with an authenticated principal. The server, not the client, authorizes every assignment and prevents cross-organization assignment.

The default API profile is fail-closed for production configuration. Read the [operations guide](docs/operations.md) before deployment.

## Evidence contract

Each run writes to `evidence/<feature-id>/` (ignored from Git): specification, plan, agent-run metadata, command output, test reports, review, costs, and final report. A run reaches `READY_FOR_REVIEW` only after all required gates have produced passing evidence.
