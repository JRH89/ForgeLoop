package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;

/** Immutable, bounded context for one repair attempt; repository content remains on the runner. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "attempt"}))
public class RepairPackage {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    // A quality-repair package may be saved before the new repair task is flushed
    // through its run collection, so persist that task as part of this association.
    @ManyToOne(optional = false, cascade = CascadeType.PERSIST) private DeliveryTask task;
    private int attempt;
    @Column(nullable = false, length = 80) private String failureCategory;
    @Column(length = 64) private String changeSha;
    @Column(length = 64) private String evidenceDigest;
    @Column(nullable = false, length = 4000) private String ownedPaths;
    @Column(nullable = false, length = 12000) private String acceptanceCriteria;
    @Column(length = 4000) private String failingTests;
    @Column(nullable = false) private Instant createdAt;

    protected RepairPackage() { }

    public RepairPackage(DeliveryTask task, String failureCategory, String evidenceDigest) {
        this(task, failureCategory, evidenceDigest, List.of());
    }

    public RepairPackage(DeliveryTask task, String failureCategory, String evidenceDigest, List<String> failingTests) {
        this.task = task;
        this.attempt = task.nextRepairPackageAttempt();
        this.failureCategory = failureCategory == null || failureCategory.isBlank() ? "EXECUTION_FAILED" : failureCategory;
        this.changeSha = task.getChangeSha();
        this.evidenceDigest = evidenceDigest;
        this.ownedPaths = String.join("\n", task.getOwnedPaths());
        this.acceptanceCriteria = task.getRun().getCriteria().stream().map(AcceptanceCriterion::getStatement)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        StringBuilder boundedTests = new StringBuilder();
        for (String test : failingTests == null ? List.<String>of() : failingTests) {
            int addition = test.length() + (boundedTests.isEmpty() ? 0 : 1);
            if (boundedTests.length() + addition > 4_000) break;
            if (!boundedTests.isEmpty()) boundedTests.append('\n');
            boundedTests.append(test);
        }
        this.failingTests = boundedTests.isEmpty() ? null : boundedTests.toString();
        this.createdAt = Instant.now();
        task.attachRepairPackage(this);
    }

    /** Static category guidance carries no provider prose, source code, prompts or credentials. */
    public String getCorrectionGuidance() {
        return switch (failureCategory) {
            case "INVALID_PROVIDER_OUTPUT", "INVALID_OUTPUT" ->
                    "Return the exact requested JSON schema, with a nonempty summary of at most 500 characters. "
                    + "Keep every changed file within the owned paths and original write boundary; include complete file content only in the changes field. "
                    + "Correct invalid metadata or scope without dropping acceptance criteria or weakening tests.";
            case "TEST_BOUNDARY_VIOLATION", "SCOPE_VIOLATION" ->
                    "Remove changes outside the owned paths or original write boundary. Test writers may edit only declared test files; "
                    + "implementation repairs must preserve tests. Do not expand scope to make the patch pass.";
            case "BUDGET_EXHAUSTED" ->
                    "Execution was refused by the spend limit. Review the remaining approved budget and accounting before another attempt; "
                    + "do not retry provider calls or change source code to bypass the limit.";
            case "OUTPUT_TOKEN_LIMIT", "CONFIGURATION_ERROR", "PERMANENT_PROVIDER_FAILURE" ->
                    "Provider execution requires operator review of the output budget or provider configuration before another attempt. "
                    + "Do not treat a truncated response or configuration failure as a source defect.";
            default -> "Inspect the referenced evidence and failing tests, then make the smallest correction within the owned paths and original write boundary.";
        };
    }

    public String getId() { return id; }
    public String getTaskId() { return task.getId(); }
    public int getAttempt() { return attempt; }
    public String getFailureCategory() { return failureCategory; }
    public String getChangeSha() { return changeSha; }
    public String getEvidenceDigest() { return evidenceDigest; }
    public List<String> getOwnedPaths() { return ownedPaths.isBlank() ? List.of() : ownedPaths.lines().toList(); }
    public List<String> getAcceptanceCriteria() { return acceptanceCriteria.isBlank() ? List.of() : acceptanceCriteria.lines().toList(); }
    public List<String> getFailingTests() { return failingTests == null || failingTests.isBlank() ? List.of() : failingTests.lines().toList(); }
    public String getCreatedAt() { return createdAt.toString(); }
}
