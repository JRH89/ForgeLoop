# ForgeLoop

ForgeLoop is an evidence-first, multi-agent software-delivery control plane. It turns a bounded GitHub issue or feature specification from any authorized repository into a reviewable change only after objective verification gates pass. Ticketly is a separate sample SaaS repository used to prove the workflow; it is not part of ForgeLoop.

## What is real today

* A Java 21 / Spring Boot 4 GraphQL control-plane API with PostgreSQL persistence, run/task/gate state, and immutable webhook-delivery records.
* A React 19 operator console that reads ForgeLoop runs from the control-plane API; it does not contain ticket CRUD.
* Docker Compose starts the control plane, operator console, and PostgreSQL with health checks.
* The harness models planning, parallel execution, verification, bounded repair, review, and evidence output. Its worker boundary is provider-agnostic and records selection rationale.
* A TypeScript MCP stdio server exposes repository, feature, and verification tools to a model host. Destructive operations are deliberately not exposed.

## Quick start

Prerequisites: Docker Desktop (or Java 21+, Maven 3.9+, Node 22+).

```sh
docker compose up --build
```

Open `http://localhost:5173`; the ForgeLoop GraphQL endpoint is `http://localhost:8090/graphql`, and health is `http://localhost:8090/actuator/health`.

For local development:

```sh
cd control-plane && mvn verify
cd ../frontend && npm ci && npm run check
cd ../mcp-server && npm ci && npm test
```

## Architecture and safety

See [docs/architecture.md](docs/architecture.md), [docs/product-spec.md](docs/product-spec.md), [docs/verification.md](docs/verification.md), and [CONTRIBUTING.md](CONTRIBUTING.md). A connected repository's versioned policy controls issue labels, branches, allowed paths, verification gates, MCP permissions, budgets, and pull-request behavior. Ticketly is an external validation repository, not a dependency.

The default API profile is fail-closed for production configuration. Read the [operations guide](docs/operations.md) before deployment.

## Evidence contract

Each run writes to `evidence/<feature-id>/` (ignored from Git): specification, plan, agent-run metadata, command output, test reports, review, costs, and final report. A run reaches `READY_FOR_REVIEW` only after all required gates have produced passing evidence.
