# Verification protocol

Required gates are selected per repository by its versioned `RepositoryPolicy` or harness profile. The following are the Ticketly sample profile gates for FEATURE-142; they are not ForgeLoop defaults for every repository:

1. Backend compile and unit/integration tests (`mvn verify`).
2. Frontend unit tests, lint, TypeScript check, and production build (`npm run check`).
3. GraphQL contract scenario: valid in-organization assignment persists and emits an audit event.
4. Authorization scenario: cross-organization assignment is rejected by the server.
5. Container health: API actuator and PostgreSQL health checks are healthy.
6. Browser scenario: an admin sees an assignment change and a non-admin cannot submit it.
7. Acceptance-criteria review, with one evidence item per criterion.

The orchestration state machine never treats an agent's completion message as proof. Every gate must have a timestamped command/result artifact. Repairs are capped per failed gate; manual intervention, skipped checks, and simulated provider results are represented explicitly in the final report.
