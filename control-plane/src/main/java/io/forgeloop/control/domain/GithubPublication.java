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
    protected GithubPublication() { }
    public GithubPublication(String featureRunId, String repository, String branch, String idempotencyKey) { this.featureRunId = featureRunId; this.repository = repository; this.branch = branch; this.idempotencyKey = idempotencyKey; }
    public void recordHeadSha(String value) { headSha = value; }
    public void recordCheckRun(long value) { checkRunId = value; }
    public void recordPullRequest(long value, boolean autoMerge) { pullRequestNumber = value; autoMergeRequested = autoMerge; deliveredAt = Instant.now(); }
    /** Records the immutable GitHub merge receipt, making webhook and polling retries idempotent. */
    public void recordMerge(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Merge SHA is required"); mergeSha = value; mergedAt = Instant.now(); }
    public boolean isDelivered() { return pullRequestNumber != null; }
    public String getId() { return id; } public String getFeatureRunId() { return featureRunId; } public String getRepository() { return repository; }
    public String getBranch() { return branch; } public String getIdempotencyKey() { return idempotencyKey; } public String getHeadSha() { return headSha; }
    public Long getCheckRunId() { return checkRunId; } public Long getPullRequestNumber() { return pullRequestNumber; } public Instant getDeliveredAt() { return deliveredAt; }
    public boolean isAutoMergeRequested() { return autoMergeRequested; } public Instant getMergedAt() { return mergedAt; } public String getMergeSha() { return mergeSha; }
}
