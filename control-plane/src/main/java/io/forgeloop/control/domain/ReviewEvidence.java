package io.forgeloop.control.domain;

import io.forgeloop.control.application.ReviewCriterionSubmission;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Immutable, checksummed result from the server-owned independent review stage. */
@Entity
public class ReviewEvidence {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private DeliveryTask task;
    @ManyToOne(optional = false) private Runner runner;
    private boolean approved;
    @Column(nullable = false, length = 2000) private String summary;
    @Column(nullable = false, length = 64, unique = true) private String digest;
    @Column(nullable = false) private Instant recordedAt;
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewCriterionAssessment> criteria = new ArrayList<>();

    protected ReviewEvidence() { }
    public ReviewEvidence(DeliveryTask task, Runner runner, boolean approved, String summary, List<ReviewCriterionSubmission> submissions) {
        this.task = task; this.runner = runner; this.approved = approved; this.summary = summary; this.recordedAt = Instant.now();
        submissions.forEach(item -> criteria.add(new ReviewCriterionAssessment(this, item.statement(), item.status(), item.evidence())));
        this.digest = digest(approved, summary, submissions);
    }
    public static String digest(boolean approved, String summary, List<ReviewCriterionSubmission> criteria) {
        try {
            String canonical = approved + "\n" + summary + "\n" + criteria.stream().map(item -> item.statement() + "\u001f" + item.status() + "\u001f" + item.evidence()).reduce((a,b) -> a + "\n" + b).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    public String getId() { return id; }
    public String getTaskId() { return task.getId(); }
    public String getRunnerId() { return runner.getId(); }
    public boolean isApproved() { return approved; }
    public String getSummary() { return summary; }
    public String getDigest() { return digest; }
    public String getRecordedAt() { return recordedAt.toString(); }
    public List<ReviewCriterionAssessment> getCriteria() { return List.copyOf(criteria); }
}
