# ForgeLoop live-run recovery implementation plan

> **For agentic workers:** Use executing-plans for root contract changes and dispatching-parallel-agents for independent failure/accounting and server repair domains. User has explicitly authorized autonomous fixes and a fresh easier canary capped at $2.

**Goal:** Correct ForgeLoop's demonstrated delivery failures and validate the real workflow, using Project Hub only as a fixture.

**Architecture:** Keep worker/parser/file guards authoritative, mirror their bounds into provider-facing schemas, retain rejected receipt usage, and preserve role/context through repairs. Add a durable operator-level cap around the isolated runner's provider calls; this is additional protection and does not claim to replace distributed server reservations.

**Tech stack:** Java 21, Maven/JUnit, Spring/JPA/PostgreSQL, Docker Compose; no new dependencies.

- [x] Reproduce incompatible patch summary/path schemas; enforce bounded summaries and task-owned prefix patterns in serialized output schemas, preserving application validation.
- [x] Preserve unsupported Anthropic schema bounds as descriptions rather than silently dropping guidance; offline test normalization and immutability.
- [x] Classify output exhaustion as non-transient with receipt, aggregate retry usage, retain unknown transport uncertainty, and journal rejected receipts locally.
- [x] Preserve independent-test role and repair context after claim, prohibit copied test-local implementations, and hold configuration failures rather than blindly spending on identical repairs.
- [x] Add durable locked, owner-only runner-wide provider spend ledger with reservation before each request, actual known settlement, and conservative retention on unknown receipt. Refuse unpriced or over-cap requests before dispatch when enabled.
- [x] Verify full runner/control-plane suites, fresh review, rebuild feature runner and local API, and use a new documentation-only fixture run with $2 server budget plus independent $2 runner cap. One provider try per task; no merge/release.
- [x] Report real run outcome, PR if delivery succeeds, actual/uncertain spend separately. Keep prior failed run intact; do not count manual completion as an autonomous success.

Offline verification: runner 437 tests, zero failures/errors, five existing platform/live-fixture skips; control-plane385 tests, zero failures/errors. Independent review found no blockers for the explicitly priced, single-attempt, fresh-ledger $2 canary.

Live validation: run b8c2c192-a032-4139-aaf7-a0e6e70aec4a reached verified/approved delivery and opened fixture PR https://github.com/JRH89/project-hub/pull/13 through ForgeLoop. The fixture is README-only. Recorded model estimates: 520350 micros; known schema-probe estimates: 3366 micros; conservative ledger including retained rejected-request holds: 933846 micros, below the authorized2000000-micro cap. Previous failed runs and attempts remain intact. The paid runner is paused after this canary.

The live canary exposed two additional transport weaknesses, both fixed with regressions: normalize only non-code commit metadata when vendor grammar cannot enforce its length, and return stable review criterion IDs instead of copying quoted canonical prose. Final runner suite:442 tests, zero failures/errors, five existing skips; control suite:385 tests, zero failures/errors.
