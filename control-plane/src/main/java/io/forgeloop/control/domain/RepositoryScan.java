package io.forgeloop.control.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Manual, read-only repository analysis requested by one organization. */
@Entity
@Table(name = "repository_scan")
public class RepositoryScan {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false) private String organizationId;
    @Column(nullable = false) private String repository;
    @Column(nullable = false) private String baseBranch;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false, length = 200) private String requestedBy;
    @Column(nullable = false) private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    @Column(length = 36) private String runnerId;
    @Column(length = 64) private String commitSha;
    @Column(length = 80) private String provider;
    @Column(length = 200) private String model;
    private long inputTokens;
    private long outputTokens;
    private long estimatedCostMicros;
    @Column(nullable = false) private boolean costKnown;
    @Column(length = 1000) private String failureSummary;
    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("severityOrder ASC, title ASC") private List<RepositoryScanFinding> findings = new ArrayList<>();

    protected RepositoryScan() { }
    public RepositoryScan(String organizationId, String repository, String baseBranch, String requestedBy) {
        this.organizationId = organizationId;
        this.repository = repository;
        this.baseBranch = baseBranch;
        this.requestedBy = requestedBy;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }
    public void claim(String runnerId) {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Repository scan is no longer pending");
        this.status = "RUNNING";
        this.runnerId = runnerId;
        this.startedAt = Instant.now();
    }
    /** Makes a scan claimable again only after a runner has stopped renewing its execution by polling. */
    public boolean requeueExpiredClaim(Instant staleBefore) {
        if (!"RUNNING".equals(status) || startedAt == null || !startedAt.isBefore(staleBefore)) return false;
        this.status = "PENDING";
        this.runnerId = null;
        this.startedAt = null;
        return true;
    }
    public void complete(String runnerId, String commitSha, String provider, String model, long inputTokens,
                         long outputTokens, long estimatedCostMicros, boolean costKnown,
                         List<RepositoryScanFinding> results) {
        requireRunner(runnerId);
        if (commitSha == null || !commitSha.matches("[0-9a-f]{40,64}") || inputTokens < 0 || outputTokens < 0
                || estimatedCostMicros < 0 || (!costKnown && estimatedCostMicros != 0))
            throw new IllegalArgumentException("Repository scan result is invalid");
        this.commitSha = commitSha;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCostMicros = estimatedCostMicros;
        this.costKnown = costKnown;
        this.findings.clear();
        results.forEach(finding -> this.findings.add(finding.attachTo(this)));
        this.status = "COMPLETE";
        this.completedAt = Instant.now();
    }
    public void fail(String runnerId, String safeSummary) {
        requireRunner(runnerId);
        this.failureSummary = safeSummary;
        this.status = "FAILED";
        this.completedAt = Instant.now();
    }
    private void requireRunner(String runnerId) {
        if (!"RUNNING".equals(status) || !this.runnerId.equals(runnerId))
            throw new IllegalStateException("Repository scan is not assigned to this runner");
    }
    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getRepository() { return repository; }
    public String getBaseBranch() { return baseBranch; }
    public String getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getCreatedAt() { return createdAt.toString(); }
    public String getStartedAt() { return startedAt == null ? null : startedAt.toString(); }
    public String getCompletedAt() { return completedAt == null ? null : completedAt.toString(); }
    public String getCommitSha() { return commitSha; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getEstimatedCostMicros() { return estimatedCostMicros; }
    public boolean isCostKnown() { return costKnown; }
    public String getFailureSummary() { return failureSummary; }
    public List<RepositoryScanFinding> getFindings() { return List.copyOf(findings); }
}
