# Stage-scoped skills implementation plan

**Goal:** Build an opt-in, provider-neutral skill pack on a feature branch.
**Spec:** ../specs/2026-10-26-stage-skills-design.md
**Execution:** Native implementation with one independent whole-branch review.
**Architecture:** Bounded classpath resources -> validated immutable selection ->
subordinate worker instructions and journal pins; existing scheduling and gates.

## Global constraints

Default-off; exact repository allowlist; no permission/schema/budget widening;
no approval prompts in autonomous procedures; no recursive skills or network loads;
no merge, production enablement, or provider charges.

## Review focus

Unknown profiles cannot fall back silently; mutable source cannot replace skills;
unsupported/deterministic stages must stay empty; request and journal must agree;
interactive drafting and independent tests must retain their distinct boundaries.

## Tasks

1. Baseline and resource contract: run runner verification; add a failing test for
   the missing bundled pack, then add bounded adapted resources and attribution.
2. Resolver: test exact role and repository routing, disabled parity, corrupt/missing
   resources, invalid configuration, immutable ordered metadata and stable hashes.
   Implement small immutable types and a deterministic classpath loader/resolver.
3. Workers: inject one pinned selection into planner, issue proposal/chat, scoped
   patch/repair/test and review requests. Test captured instructions and unchanged
   output schemas/contracts with scripted providers; preserve existing constructors.
4. Loop and records: pin selection in loop setup; compose instructions once for
   requests and journal. Test byte/digest identity and unchanged grants. Include
   pins in opted-in single-call records; verify replay compatibility.
5. Operator testing: provide offline skill inspection, configuration examples and
   pilot comparison instructions. Run full runner verify and relevant module checks.
6. Review and delivery: independent branch review, fix meaningful findings with
   regression tests, commit the tested branch and publish to both configured remotes.
   Keep master and production settings unchanged.
