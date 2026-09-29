package io.forgeloop.control.domain;

import jakarta.persistence.Column;
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
    @ManyToOne(optional = false) private DeliveryTask task;
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
        this.attempt = task.getAttempts();
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
