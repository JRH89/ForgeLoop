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

The profile is applied to dispatched worker tasks and enrolled issue-drafting jobs.
Standalone provider diagnostics and the ad hoc `generate-patch` command retain
their existing behavior because they have no server-assigned repository context.

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

Independent whole-branch review found no actionable defects and confirmed the
experiment is ready for an opt-in branch trial. Live model quality and cost
improvements remain to be measured; this is not production enablement.

## Live pilot findings

The local project-hub trial exposed full-file patch responses exhausting an 8K
output allowance when the provider also uses it for reasoning. The local pilot sets
`FORGELOOP_PATCH_MAX_OUTPUT_TOKENS=32768` to leave room for reasoning and complete
files. This bounded override accepts 8192–32768; the default remains 8192 for
compatibility with smaller models. Path, schema, retry and run-budget controls
are unchanged. Planner calls now apply the same
configured-rate cost calculation as writing tasks, so their usage contributes to
known spending instead of always appearing unpriced. Regression tests cover both.

The pilot also found that a schema-valid patch could carry a summary longer than
the existing 500-character application limit. Writing instructions now state that
limit and the existing 20-file ceiling using the validator constants; validation
is not relaxed. Sonnet 5 default thinking also exhausted long single-call writing
requests, so remaining pilot calls use an isolated Sonnet 4.6 policy with its
$3/$15 per-million token prices and the 16K writing allowance. The saved desktop
provider configuration is unchanged; each attempt pins its actual local policy.

## Recovery after the first live trial

The failed live trial remains failed. The manually recovered fixture PR is not
proof of autonomous delivery. Recovery fixes now mirror the parser's summary
limit and each task's owned prefixes into constrained output schemas. Anthropic
schema adaptation retains removed bounds in field descriptions. Independent-test
repairs keep their role and bounded failure context after claim; their procedures
require real production interfaces rather than copied test-local implementations.
Rejected output receipts retain usage, output limits stop identical retries, and
an optional durable local provider ceiling protects an isolated paid canary even
if receipt delivery or the runner process fails. See runner/README.md for its
explicit prices, POSIX filesystem requirements and non-distributed scope.

The $2 canary caught Anthropic grammar limits on long bounded summary regexes.
Summary length remains descriptive schema guidance and a strict application
invariant. The worker canonicalizes only commit prose (whitespace and a
surrogate-safe 500-character cutoff) before constructing a strict PatchPlan;
standalone strict parsing is unchanged. File paths, contents, message fields,
JSON shape, and write permissions are never normalized or relaxed. Original
provider responses remain recorded. A tiny same-ledger schema probe validates
native vendor compatibility before resuming full-context work.

Review responses now reference stable criterion IDs. ForgeLoop maps them back to
the exact server statements before recording evidence, avoiding malformed vendor
JSON when a canonical statement contains quotes or other punctuation. Legacy
well-formed statement responses remain accepted only with exact full coverage.
Malformed review output is held as a configuration error instead of automatically
paying for repeated read-only reviews.

## Completed capped canary

The corrected pipeline generated, integrated, independently reviewed and verified
a README-only change, then published https://github.com/JRH89/project-hub/pull/13.
Run `b8c2c192-a032-4139-aaf7-a0e6e70aec4a` retained its original $2 budget across
configuration corrections. Recorded run model estimates were $0.520350; the local
ledger including probes and conservative unresolved holds was $0.933846. Paid
work was paused afterwards. This validates the small documentation delivery path;
test-writing behavior is covered by regression tests and remains a separate live
quality experiment. Final validation passed 442 runner tests (five existing skips)
and 385 control-plane tests with zero failures or errors.
