# Stage-scoped skills experiment

## Intent

Test provider-neutral, Superpowers-inspired procedures inside ForgeLoop's existing
roles without importing human approval menus, recursive skill discovery, or a
second agent orchestrator. The prior discussion's curated pack approach is the
selected approach; user authorization is to implement and test it on a feature
branch, not to enable it in production or merge it.

## Scope and constraints

Ship an immutable, versioned runner resource pack and deterministic stage resolver.
The default is disabled. A runner explicitly selects `superpowers-experimental-v1`
and an exact repository allowlist. Unselected repositories keep the legacy prompts.
Each selection records ordered skill identifiers, versions, content digests and a
canonical selection digest. Skills may not change tools, output schemas, budgets,
review/merge authority, ownership, or test-first boundaries. No repository-authored
skill code or mutable repository instruction files are loaded in this iteration.

Apply stage instructions to autonomous issue proposals, interactive issue drafting,
planning, single-call scoped implementation/repair, independent test authoring,
independent review, and the guarded writing loop. Interactive issue drafting keeps
its existing explicit publication boundary. Deterministic integration, verification,
PR publication, and merge remain application-owned and receive no skill pack.

## Design

- Bundle bounded Markdown skill resources plus a manifest under runner resources.
  The resolver validates IDs, version, content digests and role/stage bindings.
- Resolve only an allowlisted profile for an exact repository. Unknown profiles,
  malformed configuration, missing resources, or hash mismatches fail before a
  provider call. Do not download executable content or recursively load skills.
- Preserve the existing fixed instructions and JSON contracts. Append selected
  workflow guidance as subordinate procedures. Skills describe evidence required;
  only existing code and gates decide whether it is sufficient.
- Pin one immutable selection for each worker attempt. Loop requests and its
  LOOP_STARTED journal must use exactly the same composed instructions/selection.
  Single-call recording journals capture selected skill metadata alongside the
  exact provider request. Enabled selections are inspectable by an offline CLI.
- The autonomous specification procedure investigates alternatives and records
  assumptions within supplied evidence; it never fabricates repository facts or
  forces approval pauses. Independent design review is not added as a new scheduler
  stage in this experiment; the existing independent review remains authoritative
  for delivery. Output schema extensions and new review rounds are deferred.
- Independent test guidance preserves the existing RED/GREEN protocol and test-file
  write boundary. Implementation cannot rewrite tests to manufacture success.
- Adapt upstream ideas with attribution and a pinned upstream reference. This is
  a maintained adaptation, not an installation of users' personal Codex skills.

## Alternatives

1. Install the complete upstream library on each runner: rejected because it is
   machine-dependent, includes human dialogues and permits inappropriate discovery.
2. Load arbitrary skills from each target repository: deferred until there is a
   reviewed trust and snapshot protocol; worktree content is untrusted today.
3. Curated packaged profiles with exact repository opt-in: chosen for this
   controlled experiment, reproducibility and compatibility with all providers.

## Validation and rollout

Use offline scripted providers and real temporary Git repositories. Test disabled
prompt parity, exact stage/repository isolation, manifest integrity, invalid config,
unchanged schemas and permissions, bounded text, loop request/journal identity, and
record/replay compatibility. No paid provider call or deployment is authorized by
this implementation. Document how to compare baseline and enabled runs on a pilot
repository, including correctness, cost, retries, review failures and interventions.
