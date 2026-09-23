# Verification policy and evidence

ForgeLoop never accepts an arbitrary verification command from a task or operator. An administrator configures a versioned repository policy, and every run snapshots that policy before the planner creates work. Required gates become `VERIFICATION` tasks after the planner DAG; optional gates are retained as `SKIPPED_BY_POLICY` and cannot contribute criterion coverage.

Each policy contains a gate name, check kind (`CONTAINER`, `BROWSER`, `SECURITY`, `CONTRACT`, or `COMPOSE`), an immutable image digest, an argv array, an explicit network decision, a timeout, and a criterion coverage rule. The current coverage rule is deliberately strict: `ALL` means every acceptance criterion needs the passing gate evidence.

## Ticketly policy

The following mutation configures Ticketly without adding Ticketly-specific logic to either ForgeLoop service. Image tags are shown in comments only; the stored values are immutable digests resolved on 2026-09-22.

```graphql
mutation ConfigureTicketlyVerification {
  configureRepositoryVerification(
    repository: "JRH89/Ticketly"
    policies: [
      { name: "backend", kind: "CONTAINER", imageDigest: "maven@sha256:8b2f036477a5bc9fbeb16cfb7301c484d7fff727b1c4907301ac665526bd7a8e", command: ["mvn", "-f", "backend/pom.xml", "verify"], networkPolicy: "EGRESS", timeoutSeconds: 900, required: true, criterionCoverage: "ALL" }
      { name: "frontend", kind: "CONTAINER", imageDigest: "node@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1", command: ["sh", "-c", "cd frontend && npm ci --ignore-scripts && npm run check"], networkPolicy: "EGRESS", timeoutSeconds: 900, required: true, criterionCoverage: "ALL" }
      { name: "compose", kind: "COMPOSE", imageDigest: "docker@sha256:018edbc908e08fcc9dbf029c812c34251e9b4719e6f71ca0e5eae2a987d014ca", command: ["docker", "compose", "-f", "docker-compose.yml", "config", "--quiet"], networkPolicy: "NONE", timeoutSeconds: 120, required: true, criterionCoverage: "ALL" }
      { name: "browser", kind: "BROWSER", imageDigest: "mcr.microsoft.com/playwright@sha256:eff16c30e6f3f4af0a03fa4b706120d5e9b0891c344a27d64559aff5900a4a27", command: ["sh", "-c", "cd frontend && npm ci --ignore-scripts && npm exec playwright test -- --list"], networkPolicy: "EGRESS", timeoutSeconds: 900, required: true, criterionCoverage: "ALL" }
      { name: "authorization", kind: "CONTRACT", imageDigest: "maven@sha256:8b2f036477a5bc9fbeb16cfb7301c484d7fff727b1c4907301ac665526bd7a8e", command: ["mvn", "-f", "backend/pom.xml", "-Dtest=TicketAssignmentServiceTest", "test"], networkPolicy: "EGRESS", timeoutSeconds: 600, required: true, criterionCoverage: "ALL" }
    ]
  ) { repository policyRevision requiredGates verificationPolicies { name kind imageDigest command networkPolicy timeoutSeconds required criterionCoverage } }
}
```

`EGRESS` permits package and Maven artifact retrieval; it does not inject credentials. Production verification images should pre-cache locked dependencies so these gates can move to `NONE`.

## Evidence integrity and secret handling

The runner copies the read-only Git worktree into an ephemeral container filesystem, executes the policy argv, bounds runtime and output, redacts common credential forms, and writes an atomic JSON artifact plus SHA-256 manifest. It verifies that local bundle, uploads the exact bytes through the active authenticated lease, and receives an opaque `artifact://` reference only after the control plane performs checksum and storage read-after-write verification. The final report includes start/end timestamps, exit and timeout state, output checksum, bundle checksum, durable artifact reference, container digest, and command argv. The control plane recomputes both report checksums, rejects metadata that differs from the run policy snapshot, and rejects output that still resembles a raw credential.

Only `PASSED` required gates cover acceptance criteria. `FAILED`, `TIMED_OUT`, `SKIPPED_BY_POLICY`, and `MANUAL_OVERRIDE` never make a run `READY_FOR_REVIEW`; GitHub delivery therefore cannot create a branch or PR for them. A failed or timed-out task enters the existing bounded repair lifecycle and retains the evidence digest in its repair package.
