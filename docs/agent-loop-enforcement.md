# Agent-loop enforcement (Slice 4a)

This document describes the fail-closed guard layer added to the dormant agent loop. It is not an enablement guide: no runner dispatch path invokes the loop, and this slice must not enable one.

## Before the first provider request

The runner snapshots dispatched enforcement inputs in an immutable `EnforcementDescriptor`. Its canonical SHA-256 and rule-version are recorded in `LOOP_STARTED`. `EnforcementPreflight` validates the descriptor before constructing or invoking the provider conversation. Invalid or unsupported settings produce `POLICY_HOLD`, a `LOOP_ENDED` record, and no provider request.

The descriptor is derived from task policy and gate names, not the issue text, specification, MCP context, or model-produced content. That keeps policy authority outside prompt-controlled data.

## Fixed tool boundary

Every loop tool call passes through one fixed interceptor chain:

1. `credential-files` denies access to credential-bearing paths and removes matching files from directory-search results. Example/template files remain available.
2. `protected-paths` denies writes to `.github/workflows/**` (case-insensitive) and holds at finish if such a path was changed.
3. `write-boundary` enforces the dispatched `ANY`, `TESTS_ONLY`, or `NO_TESTS` mode before a write and rechecks changed paths at finish.
4. `secret-content` denies writes containing supported token-shaped values or private-key blocks.
5. `result-redaction` redacts supported tokens/private keys from file, search, and gate output before the model or journal receives it.

The first non-allow `before` decision wins. A rule exception, missing decision, or invalid/failed `after` hook is a `RULE_FAILED` hold. After-hooks may change only content and metadata; status, category, and post-images are immutable. Result changes are recorded in the journal without exposing the original credential.

## Hold and evidence behavior

Enforcement holds are `RULE_INPUT_MISSING`, `PREREQUISITE_MISSING`, `RULE_FAILED`, or `BOUNDARY_BREACHED`. A hold stops the rest of the model's current tool batch, prevents finish/commit, ends the loop as `POLICY_HOLD`, and records the class, rule/check, reason, and enforcement digest. Tool grant refusals are journaled as `DENY` under `tool-grant`.

The control-plane lease hold maps these classes to `ENFORCEMENT_RULE_INPUT_MISSING`, `ENFORCEMENT_PREREQUISITE_MISSING`, `ENFORCEMENT_RULE_FAILED`, and `ENFORCEMENT_BOUNDARY_BREACHED`, respectively. All four are accepted only for an active acknowledged agent-loop lease and each creates a HIGH-severity escalation. The runner-to-control-plane mapping is documented here but is not wired into dispatch in this slice.

The descriptor can represent RED prerequisite inputs, but current server dispatch does not populate them; binding and validating live RED evidence is Slice 4b. Repository-configured protected globs, audited workflow opt-out, and finish-gate behavior are Slice 4c. Spend reservations are Slice 4d. None of these future slices is implied complete by 4a.

## Verification

Runner verification uses the local portable JDK/Maven toolchain:

```powershell
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'ForgeLoop\tools\temurin21\jdk-21.0.12.1+1'
$mavenExe = Join-Path $env:LOCALAPPDATA 'ForgeLoop\tools\apache-maven-3.9.12\bin\mvn.cmd'
& $mavenExe -B verify
```

The test suite covers the descriptor/preflight, credential paths and redaction, protected/test boundaries, gateway exception handling, and loop holds. No provider-backed work was run; the loop remains dormant.
