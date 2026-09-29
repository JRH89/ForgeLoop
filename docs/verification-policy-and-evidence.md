# Verification policy and evidence

ForgeLoop never accepts an arbitrary verification command from a task or operator. An administrator configures a versioned repository policy, and every run snapshots that policy before the planner creates work. Required gates become `VERIFICATION` tasks after the planner DAG; optional gates are retained as `SKIPPED_BY_POLICY` and cannot contribute criterion coverage.

Each policy contains a gate name, check kind (`CONTAINER`, `BROWSER`, `SECURITY`, `CONTRACT`, or `COMPOSE`), an immutable image digest, an argv array, an explicit network decision, a timeout, and a criterion coverage rule. The current coverage rule is deliberately strict: `ALL` means every acceptance criterion needs the passing gate evidence.

## Example repository policy

The following mutation illustrates repository-owned verification settings. Image tags are shown in comments only; production policies store immutable image digests.

```graphql
mutation ConfigureRepositoryVerification {
  configureRepositoryVerification(
    repository: "acme/service"
    policies: [
      { name: "backend", kind: "CONTAINER", imageDigest: "maven@sha256:8b2f036477a5bc9fbeb16cfb7301c484d7fff727b1c4907301ac665526bd7a8e", command: ["mvn", "-f", "backend/pom.xml", "verify"], networkPolicy: "EGRESS", timeoutSeconds: 900, required: true, criterionCoverage: "ALL" }
      { name: "frontend", kind: "CONTAINER", imageDigest: "node@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1", command: ["sh", "-c", "cd frontend && npm ci --ignore-scripts && npm run check"], networkPolicy: "EGRESS", timeoutSeconds: 900, required: true, criterionCoverage: "ALL" }
      { name: "compose", kind: "COMPOSE", imageDigest: "docker@sha256:018edbc908e08fcc9dbf029c812c34251e9b4719e6f71ca0e5eae2a987d014ca", command: ["docker", "compose", "-f", "docker-compose.yml", "config", "--quiet"], networkPolicy: "NONE", timeoutSeconds: 120, required: true, criterionCoverage: "ALL" }
      { name: "browser", kind: "BROWSER", imageDigest: "mcr.microsoft.com/playwright@sha256:eff16c30e6f3f4af0a03fa4b706120d5e9b0891c344a27d64559aff5900a4a27", command: ["sh", "-c", "cd frontend && npm ci --ignore-scripts && npm exec playwright test -- --list"], networkPolicy: "EGRESS", timeoutSeconds: 900, required: true, criterionCoverage: "ALL" }
      { name: "authorization", kind: "CONTRACT", imageDigest: "maven@sha256:8b2f036477a5bc9fbeb16cfb7301c484d7fff727b1c4907301ac665526bd7a8e", command: ["mvn", "-f", "backend/pom.xml", "-Dtest=RepositoryAuthorizationTest", "test"], networkPolicy: "EGRESS", timeoutSeconds: 600, required: true, criterionCoverage: "ALL" }
      { name: "unit", kind: "CONTAINER", imageDigest: "node@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1", command: ["sh", "-c", "JEST_JUNIT_OUTPUT_DIR=/forgeloop/test-report npx jest --reporters=default --reporters=jest-junit"], networkPolicy: "EGRESS", timeoutSeconds: 900, required: true, criterionCoverage: "ALL", testReport: "JUNIT_XML" }
    ]
  ) { repository policyRevision requiredGates verificationPolicies { name kind imageDigest command networkPolicy timeoutSeconds required criterionCoverage testReport } }
}
```

## Optional JUnit reports and test-first path rules

Test-first delivery requires a required verification policy that writes JUnit XML under `/forgeloop/test-report`. The example above configures a `unit` gate with `testReport: "JUNIT_XML"`; include the full set of existing repository policies when replacing policy configuration. Then enable test-first mode with repository-relative globs. `**` matches whole path segments, `*` matches within one segment, and `?` matches one character; braces, character classes, and negation are intentionally unsupported.

```graphql
mutation EnableTestFirst {
  configureRepositoryTestFirst(
    repository: "acme/service"
    testGate: "unit"
    testPaths: ["src/test/**", "**/*_test.go", "**/*.test.ts"]
  ) { repository policyRevision testFirstGate testPathGlobs }
}
```

Commands must use the literal report path because policy argv is passed directly and is not shell-expanded. Examples:

| Stack | Policy command |
| --- | --- |
| pytest | `["pytest", "--junitxml=/forgeloop/test-report/pytest.xml"]` |
| Jest with `jest-junit` installed | `["sh", "-c", "JEST_JUNIT_OUTPUT_DIR=/forgeloop/test-report npx jest --reporters=default --reporters=jest-junit"]` |
| Maven Surefire | Run `mvn -B test`, then copy every `*/target/surefire-reports/*.xml` file beneath `/forgeloop/test-report`, preserving module paths and returning Maven's original exit status. |
| Gradle | Run `./gradlew test`, then copy every `*/build/test-results/*/*.xml` file beneath `/forgeloop/test-report`, preserving module paths and returning Gradle's original exit status. |

The JUnit mount is separate from the writable task workspace. This prevents a committed `test-results/` directory from shadowing or forging the report directory.

`EGRESS` permits package and Maven artifact retrieval; it does not inject credentials. Production verification images should pre-cache locked dependencies so these gates can move to `NONE`.

## Evidence integrity and secret handling

The runner copies the read-only Git worktree into an ephemeral container filesystem, executes the policy argv, bounds runtime and output, redacts common credential forms, and writes an atomic JSON artifact plus SHA-256 manifest. It verifies that local bundle, uploads the exact bytes through the active authenticated lease, and receives an opaque `artifact://` reference only after the control plane performs checksum and storage read-after-write verification. The final report includes start/end timestamps, exit and timeout state, output checksum, bundle checksum, durable artifact reference, container digest, and command argv. The control plane recomputes both report checksums, rejects metadata that differs from the run policy snapshot, and rejects output that still resembles a raw credential.

Only `PASSED` required gates cover acceptance criteria. `FAILED`, `TIMED_OUT`, `SKIPPED_BY_POLICY`, and `MANUAL_OVERRIDE` never make a run `READY_FOR_REVIEW`; GitHub delivery therefore cannot create a branch or PR for them. A failed or timed-out task enters the existing bounded repair lifecycle and retains the evidence digest in its repair package.
