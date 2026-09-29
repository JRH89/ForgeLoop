# Agent-loop enforcement (Slices 4a–4c)

This document describes the fail-closed guard layer added to the dormant agent loop. It is not an enablement guide: no runner dispatch path invokes the loop, and this slice must not enable one.

## Before the first provider request

The runner snapshots dispatched enforcement inputs in an immutable `EnforcementDescriptor`. Its canonical SHA-256 and rule-version are recorded in `LOOP_STARTED`. `EnforcementPreflight` validates the descriptor before constructing or invoking the provider conversation. Invalid or unsupported settings produce `POLICY_HOLD`, a `LOOP_ENDED` record, and no provider request.

For test-first implementation, backend, and frontend tasks, the control plane dispatches the matching test writer ID plus its current passing RED target SHA and evidence digest. Preflight requires the proof target to equal the base SHA resolved by the runner. A missing, malformed, or stale proof is a `PREREQUISITE_MISSING` hold before the first provider turn. Other task roles and non-test-first runs do not receive this prerequisite.

The descriptor is derived from task policy, the run's snapshotted repository enforcement settings, and dispatched gate names, not the issue text, specification, MCP context, or model-produced content. That keeps policy authority outside prompt-controlled data. Its canonical digest includes custom protected globs, the workflow opt-out, and the finish gate.

## Fixed tool boundary

Every loop tool call passes through one fixed interceptor chain:

1. `credential-files` denies access to credential-bearing paths and removes matching files from directory-search results. Example/template files remain available.
2. `protected-paths` denies writes to `.github/workflows/**` (case-insensitive) by default, plus repository-configured protected globs. An audited repository opt-out lifts only the built-in workflow glob; its custom globs remain enforced.
3. `write-boundary` enforces the dispatched `ANY`, `TESTS_ONLY`, or `NO_TESTS` mode before a write and rechecks changed paths at finish.
4. `secret-content` denies writes containing supported token-shaped values or private-key blocks.
5. `finish-gate` (when configured) redirects `finish` to the named `run_gate` unless the latest recorded run happened after the last successful write and meets the role-specific completion rule.
6. `result-redaction` redacts supported tokens/private keys from file, search, and gate output before the model or journal receives it.

The first non-allow `before` decision wins. A rule exception, missing decision, or invalid/failed `after` hook is a `RULE_FAILED` hold. After-hooks may change only content and metadata; status, category, and post-images are immutable. Result changes are recorded in the journal without exposing the original credential.

Repository administrators configure zero to 64 protected path globs with `configureRepositoryEnforcement`. These use the existing case-sensitive `TestPathGlobs` language. The built-in workflow deny remains case-insensitive to account for case-insensitive worktrees; opting out disables only that built-in rule. The optional finish gate must name a verification policy from that repository and remains off by default. For ordinary writers, its latest run must pass; for `TESTS_ONLY` writers, any completed non-timeout run is enough because the new tests are expected to fail. This advisory run can gate the model's `finish` call but never replaces server-authoritative verification evidence.

## Hold and evidence behavior

Enforcement holds are `RULE_INPUT_MISSING`, `PREREQUISITE_MISSING`, `RULE_FAILED`, or `BOUNDARY_BREACHED`. A hold stops the rest of the model's current tool batch, prevents finish/commit, ends the loop as `POLICY_HOLD`, and records the class, rule/check, reason, and enforcement digest. Tool grant refusals are journaled as `DENY` under `tool-grant`.

The control-plane lease hold maps these classes to `ENFORCEMENT_RULE_INPUT_MISSING`, `ENFORCEMENT_PREREQUISITE_MISSING`, `ENFORCEMENT_RULE_FAILED`, and `ENFORCEMENT_BOUNDARY_BREACHED`, respectively. All four are accepted only for an active acknowledged agent-loop lease and each creates a HIGH-severity escalation. The runner-to-control-plane mapping is documented here but is not wired into dispatch in this slice.

Slice 4b binds RED prerequisites to the current `TestCheckEvidence` record and transports them through GraphQL into the runner task and enforcement fingerprint. The derivation follows the `RED_CHECK` edge and its independent test writer, separately from the execution-base helper, so dependency ordering cannot make both checks agree on the same wrong input. Slice 4c snapshots repository-configured protected globs, an audited workflow opt-out, and an optional finish gate into each submitted run. Spend reservations are Slice 4d.

## Verification

Runner verification uses the local portable JDK/Maven toolchain:

```powershell
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'ForgeLoop\tools\temurin21\jdk-21.0.12.1+1'
$mavenExe = Join-Path $env:LOCALAPPDATA 'ForgeLoop\tools\apache-maven-3.9.12\bin\mvn.cmd'
& $mavenExe -B verify
```

Verification on the local integration stack: control-plane `mvn -B verify` passed (299 tests); runner `mvn -B verify` passed (233 tests, 1 existing symlink-permission skip); harness `mvn -B verify` passed (2 tests). The suites cover descriptor/preflight, repository-policy parsing and fingerprints, snapshot persistence, RED proof transport, custom protected paths, workflow opt-out, finish-gate redirects, credential paths and redaction, gateway exception handling, and loop holds. No provider-backed work was run; the loop remains dormant.
