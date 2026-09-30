package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Idempotency record for every externally visible GitHub delivery operation. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "featureRunId"))
public class GithubPublication {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false) private String featureRunId;
    @Column(nullable = false) private String repository;
    @Column(nullable = false) private String branch;
    @Column(nullable = false) private String idempotencyKey;
    private String headSha; private Long checkRunId; private Long pullRequestNumber; private Instant deliveredAt;
    private boolean autoMergeRequested; private Instant mergedAt; private String mergeSha;
    private Integer sourceIssueNumber; private Instant sourceIssueClosedAt;
    protected GithubPublication() { }
    public GithubPublication(String featureRunId, String repository, String branch, String idempotencyKey) { this.featureRunId = featureRunId; this.repository = repository; this.branch = branch; this.idempotencyKey = idempotencyKey; }
    public void recordHeadSha(String value) { headSha = value; }
    public void recordCheckRun(long value) { checkRunId = value; }
    public void recordPullRequest(long value, boolean autoMerge) { pullRequestNumber = value; autoMergeRequested = autoMerge; deliveredAt = Instant.now(); }
    /** Stores only the issue identity needed to close the source issue after a verified merge. */
    public void linkSourceIssue(int issueNumber) {
        if (issueNumber < 1) throw new IllegalArgumentException("Source issue number must be positive");
        if (sourceIssueNumber != null && sourceIssueNumber != issueNumber) throw new IllegalStateException("Source issue cannot change after publication");
        sourceIssueNumber = issueNumber;
    }
    /** Marks issue closure only after GitHub confirms the state update. */
    public void recordSourceIssueClosed() {
        if (sourceIssueNumber == null) throw new IllegalStateException("Publication has no source issue");
        if (mergedAt == null) throw new IllegalStateException("Source issue cannot close before the pull request merges");
        if (sourceIssueClosedAt == null) sourceIssueClosedAt = Instant.now();
    }
    /** Records the immutable GitHub merge receipt, making webhook and polling retries idempotent. */
    public void recordMerge(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Merge SHA is required"); mergeSha = value; mergedAt = Instant.now(); }
    public boolean isDelivered() { return pullRequestNumber != null; }
    public String getId() { return id; } public String getFeatureRunId() { return featureRunId; } public String getRepository() { return repository; }
    public String getBranch() { return branch; } public String getIdempotencyKey() { return idempotencyKey; } public String getHeadSha() { return headSha; }
    public Long getCheckRunId() { return checkRunId; } public Long getPullRequestNumber() { return pullRequestNumber; } public Instant getDeliveredAt() { return deliveredAt; }
    public boolean isAutoMergeRequested() { return autoMergeRequested; } public Instant getMergedAt() { return mergedAt; } public String getMergeSha() { return mergeSha; }
    public Integer getSourceIssueNumber() { return sourceIssueNumber; } public Instant getSourceIssueClosedAt() { return sourceIssueClosedAt; }
}
