# Stage-scoped skills experiment

This feature is opt-in and provider-neutral. The runner bundles a reviewed,
Superpowers-inspired pack; it does not load the runner user's personal skills,
download instructions, discover nested skills, or execute a skill as a program.
The source is `runner/src/main/resources/skills/superpowers-experimental-v1`.
`manifest.json` pins the upstream reference, each procedure's version/content
hash, and its permitted stages. Upstream MIT license and attribution are included.

## Enable a pilot

Build and test this feature branch with Java 21 and Maven:

```sh
mvn -f runner/pom.xml verify
```

In the shell that will launch the test runner:

```sh
export FORGELOOP_SKILL_PROFILE=superpowers-experimental-v1
export FORGELOOP_SKILL_REPOSITORIES=your-org/your-test-repository
java -cp runner/target/runner-0.1.0.jar io.forgeloop.runner.RunnerMain skills-inspect PLANNER your-org/your-test-repository
```

Inspection is offline: it needs no identity file, provider key, control-plane
connection, or model call. Its JSON contains enabled state, profile, stage,
repository, pack version, manifest hash, ordered procedure references, and the
selection hash. Try `REPAIR`, `INDEPENDENT_TEST`, `REVIEW`, and `AI_CHAT` to see the
differences. `MERGE` and unselected repositories return `enabled:false`.

The repository allowlist is required, exact and case-sensitive. Comma-separated
entries are supported; wildcards, empty entries, unknown profiles, and malformed
names fail closed. The pack itself is checked before a selected worker calls its
provider. With the profile unset or `off`, no resource is loaded and the original
provider instructions are retained byte for byte. Stop and restart the runner
without the profile variable to disable it for future attempts; do not change
configuration in the middle of a run.

Launch the normal runner with its existing identity, provider policy and work
settings from that same shell. For Docker, pass both variables explicitly with
`-e`; for the desktop application, launch it from the configured shell. Installing
a released runner from another branch does not include this experiment. The
multi-turn loop still separately requires `FORGELOOP_AGENT_LOOP_ENABLED=true` and
a repository loop policy. These skill variables never enable the loop themselves.
No deployment or paid pilot is performed by the automated tests.

## Stage assignments

| Worker role | Procedures |
| --- | --- |
| ISSUE_SPECIFICATION | Autonomous specification |
| AI_CHAT | Interactive issue refinement |
| PLANNER | Structured planning |
| IMPLEMENTATION, BACKEND, FRONTEND | Scoped implementation; evidence-based reporting |
| INDEPENDENT_TEST | Independent test design; evidence-based reporting |
| REPAIR | Scoped implementation; systematic repair; evidence-based reporting |
| REVIEW | Independent review; evidence-based reporting |
| Integration, verification, RED/GREEN checks, PR preparation/publication, merge | No agent procedures; existing application code owns these operations |

Autonomous specification has no routine human approval ceremony. It works within
supplied evidence and records material uncertainty instead of inventing facts.
Interactive drafting preserves explicit issue publication and may ask a focused
question through its existing assistant-message field. The experiment adds no new
independent design-review stage: existing static review and execution gates remain
the delivery boundary. Task schemas are unchanged, so the planner still emits its
validated DAG rather than a Markdown implementation plan.

## Trust and evidence

Skills are subordinate to the fixed worker instructions and output contract.
They cannot grant tools, create agents, alter budgets or required checks, mutate
test-file boundaries, authorize publication/merge, or override cancellation.
Actual enforcement continues in the control plane, tool gateway, patch writer,
lease lifecycle and verification code. In particular, implementation cannot rewrite
independent tests simply because a generic TDD procedure would normally write both.

The single-call adapter pins one selection before provider retries. Opted-in
run-record CALL_REQUESTED entries store its digest-only metadata with the actual
composed instructions and vendor-request checksum. Multi-turn LOOP_STARTED records
store that same selection plus the exact instructions/hash used for every turn.
The metadata contains no raw procedure text; local instruction records contain the
public packaged procedures as part of the full request. Replay rebuilds the
original composed request without consulting today's pack.

Current issue-proposal and Issue Chat jobs do not have the run-journal lifecycle;
they receive the procedures, but their existing control-plane activity records do
not gain skill metadata in this experiment. The control-plane policy snapshot also
does not yet include runner-local skill settings. Use the runner revision/JAR hash
and recorded selection when comparing delivery runs; freeze the runner configuration
for each pilot. Server-managed profiles and per-repository file selection require a
later protocol/migration. Target repository `.forgeloop/skills.*` files are not read.

## Compare baseline and enabled behavior

Use a disposable pilot repository, fixed starting commit, the same issue set,
model/provider policy, verification gates, concurrency and spending limits.
Keep baseline and enabled runs distinguishable by runner configuration/revision
and their selection records. Repeating trials helps separate prompt effects from
model variation. Record:

- independently verified acceptance-criterion coverage and false completion claims;
- number and causes of repairs, concrete review defects, and manual interventions;
- known model cost, tokens, wall time, and requests with unknown pricing;
- output-schema failures, policy holds, and test-boundary violations.

Offline tests establish routing, integrity, output-contract compatibility and
permission boundaries. They do not demonstrate improved model quality. Run a paid
pilot only with the repository owner's existing authorization and budget.

## Updating the pack

Edit the maintained procedures and manifest together, update affected content
hashes and versions, review their stage assignments, and rerun runner verification.
Do not replace pinned contents with an upstream `latest` download. An upstream
upgrade is a reviewed dependency change; its human interactions, tool names and
orchestration assumptions must be adapted explicitly.

## Branch validation

Local verification on this feature branch: full runner Maven verification passed
411 tests with zero failures/errors and six existing platform/live-fixture skips.
The shaded JAR includes the pack, manifest, license and attribution. Offline CLI
checks selected the planner pack for an allowlisted repository and returned
`enabled:false` for merge and the default-off case. No live provider was called.
