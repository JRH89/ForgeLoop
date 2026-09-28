package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Explicitly requested, runner-generated issue draft; creation of the GitHub issue remains a human action. */
@Entity
@Table(name = "repository_issue_proposal")
public class RepositoryIssueProposal {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false, length = 255) private String organizationId;
    @Column(nullable = false, length = 255) private String repository;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "finding_id", nullable = false)
    private RepositoryScanFinding finding;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false, length = 200) private String requestedBy;
    @Column(nullable = false) private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    @Column(length = 36) private String runnerId;
    @Column(length = 500) private String failureSummary;
    @Column(length = 200) private String proposedTitle;
    @Column(columnDefinition = "text") private String proposedBody;
    @Column(columnDefinition = "text") private String proposedCriteria;
    @Column(length = 80) private String provider;
    @Column(length = 200) private String model;
    private long inputTokens;
    private long outputTokens;
    private long estimatedCostMicros;
    @Column(nullable = false) private boolean costKnown;
    private Integer issueNumber;
    @Column(length = 500) private String issueUrl;
    @Column(length = 500) private String rejectionReason;

    protected RepositoryIssueProposal() { }

    public RepositoryIssueProposal(String organizationId, RepositoryScanFinding finding, String requestedBy) {
        this.organizationId = organizationId;
        this.repository = finding.getScan().getRepository();
        this.finding = finding;
        this.requestedBy = requestedBy;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public void claim(String runnerId) {
        if (!"PENDING".equals(status) || runnerId == null || runnerId.isBlank())
            throw new IllegalStateException("Issue proposal is no longer pending");
        this.status = "RUNNING";
        this.runnerId = runnerId;
        this.startedAt = Instant.now();
        this.failureSummary = null;
    }

    /** A claim expires only after its runner has gone quiet for the same recovery window as scans. */
    public boolean requeueExpiredClaim(Instant staleBefore) {
        if (!"RUNNING".equals(status) || startedAt == null || !startedAt.isBefore(staleBefore)) return false;
        status = "PENDING";
        runnerId = null;
        startedAt = null;
        return true;
    }

    public void complete(String runnerId, String title, String body, List<String> acceptanceCriteria,
                         String provider, String model, long inputTokens, long outputTokens,
                         long estimatedCostMicros, boolean costKnown) {
        requireRunner(runnerId);
        validateUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
        if (title == null || title.isBlank() || title.length() > 200 || body == null || body.isBlank() || body.length() > 12000
                || acceptanceCriteria == null || acceptanceCriteria.isEmpty() || acceptanceCriteria.size() > 10
                || acceptanceCriteria.stream().anyMatch(item -> item == null || item.isBlank() || item.length() > 400))
            throw new IllegalArgumentException("Generated issue proposal is invalid");
        this.proposedTitle = title.trim();
        this.proposedBody = body.trim();
        this.proposedCriteria = String.join("\n", acceptanceCriteria.stream().map(String::trim).toList());
        setUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
        this.status = "READY";
        this.completedAt = Instant.now();
    }

    public void fail(String runnerId, String summary, String provider, String model, long inputTokens, long outputTokens,
                     long estimatedCostMicros, boolean costKnown) {
        requireRunner(runnerId);
        if (provider != null) {
            validateUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
            setUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
        }
        failureSummary = summary == null || summary.isBlank() ? "The runner could not generate this issue proposal." : summary;
        status = "FAILED";
        completedAt = Instant.now();
    }

    public void approve(int number, String url) {
        if (!"READY".equals(status) || number < 1 || url == null || !url.startsWith("https://github.com/"))
            throw new IllegalStateException("Only a ready issue proposal can be published");
        finding.recordGithubIssue(number, url);
        issueNumber = number;
        issueUrl = url;
        status = "APPROVED";
    }

    public void reject(String reason) {
        if (!"READY".equals(status)) throw new IllegalStateException("Only a ready issue proposal can be rejected");
        rejectionReason = reason;
        status = "REJECTED";
        completedAt = Instant.now();
    }

    private void requireRunner(String candidate) {
        if (!"RUNNING".equals(status) || runnerId == null || !runnerId.equals(candidate))
            throw new IllegalStateException("Issue proposal is not assigned to this runner");
    }

    private void setUsage(String provider, String model, long inputTokens, long outputTokens,
                          long estimatedCostMicros, boolean costKnown) {
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCostMicros = estimatedCostMicros;
        this.costKnown = costKnown;
    }

    private static void validateUsage(String provider, String model, long inputTokens, long outputTokens,
                                      long estimatedCostMicros, boolean costKnown) {
        if (!List.of("anthropic", "openai", "gemini", "local").contains(provider) || model == null || model.isBlank() || model.length() > 200
                || inputTokens < 0 || outputTokens < 0 || estimatedCostMicros < 0 || (!costKnown && estimatedCostMicros != 0))
            throw new IllegalArgumentException("Issue proposal usage metadata is invalid");
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getRepository() { return repository; }
    public RepositoryScanFinding getFinding() { return finding; }
    public String getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getCreatedAt() { return createdAt.toString(); }
    public String getStartedAt() { return startedAt == null ? null : startedAt.toString(); }
    public String getCompletedAt() { return completedAt == null ? null : completedAt.toString(); }
    public String getFailureSummary() { return failureSummary; }
    public String getProposedTitle() { return proposedTitle; }
    public String getProposedBody() { return proposedBody; }
    public List<String> getAcceptanceCriteria() { return proposedCriteria == null ? List.of() : Arrays.stream(proposedCriteria.split("\\n")).filter(value -> !value.isBlank()).toList(); }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getEstimatedCostMicros() { return estimatedCostMicros; }
    public boolean isCostKnown() { return costKnown; }
    public Integer getIssueNumber() { return issueNumber; }
    public String getIssueUrl() { return issueUrl; }
}
