package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Tenant-owned conversation for drafting an issue; the runner only proposes and never executes it. */
@Entity
@Table(name = "issue_conversation")
public class IssueConversation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false, length = 255) private String organizationId;
    @Column(nullable = false, length = 255) private String repository;
    @Column(nullable = false, length = 200) private String createdBy;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant startedAt;
    @Column(length = 36) private String runnerId;
    @Column(length = 500) private String failureSummary;
    @Column(length = 200) private String draftTitle;
    @Column(columnDefinition = "text") private String draftBody;
    @Column(columnDefinition = "text") private String draftCriteria;
    @Column(length = 80) private String provider;
    @Column(length = 200) private String model;
    @Column(nullable = false) private long inputTokens;
    @Column(nullable = false) private long outputTokens;
    @Column(nullable = false) private long estimatedCostMicros;
    @Column(nullable = false) private boolean costKnown;
    private Integer issueNumber;
    @Column(length = 500) private String issueUrl;

    protected IssueConversation() { }

    public IssueConversation(String organizationId, String repository, String createdBy) {
        this.organizationId = organizationId;
        this.repository = repository;
        this.createdBy = createdBy;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void queueNextTurn() {
        if ("PENDING".equals(status) || "RUNNING".equals(status) || "ISSUE_CREATED".equals(status))
            throw new IllegalStateException("This conversation cannot accept a message right now");
        status = "PENDING";
        runnerId = null;
        startedAt = null;
        failureSummary = null;
        updatedAt = Instant.now();
    }

    public void claim(String runnerId) {
        if (!"PENDING".equals(status) || runnerId == null || runnerId.isBlank())
            throw new IllegalStateException("Issue conversation is no longer pending");
        status = "RUNNING";
        this.runnerId = runnerId;
        startedAt = Instant.now();
    }

    public boolean requeueExpiredClaim(Instant staleBefore) {
        if (!"RUNNING".equals(status) || startedAt == null || !startedAt.isBefore(staleBefore)) return false;
        status = "PENDING";
        runnerId = null;
        startedAt = null;
        return true;
    }

    public void complete(String runnerId, String assistantMessage, String title, String body, List<String> criteria,
                         String provider, String model, long inputTokens, long outputTokens,
                         long estimatedCostMicros, boolean costKnown) {
        requireRunner(runnerId);
        validateUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
        if (assistantMessage == null || assistantMessage.isBlank() || assistantMessage.length() > 2000
                || title == null || title.isBlank() || title.length() > 200
                || body == null || body.isBlank() || body.length() > 12000
                || criteria == null || criteria.isEmpty() || criteria.size() > 10
                || criteria.stream().anyMatch(item -> item == null || item.isBlank() || item.length() > 400))
            throw new IllegalArgumentException("AI issue draft is invalid");
        draftTitle = title.trim();
        draftBody = body.trim();
        draftCriteria = String.join("\n", criteria.stream().map(String::trim).toList());
        setUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
        status = "READY";
        runnerId = null;
        startedAt = null;
        failureSummary = null;
        updatedAt = Instant.now();
    }

    public void fail(String runnerId, String summary, String provider, String model, long inputTokens, long outputTokens,
                     long estimatedCostMicros, boolean costKnown) {
        requireRunner(runnerId);
        if (provider != null) {
            validateUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
            setUsage(provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown);
        }
        failureSummary = summary == null || summary.isBlank() ? "The runner could not draft this issue." : summary;
        status = "FAILED";
        runnerId = null;
        startedAt = null;
        updatedAt = Instant.now();
    }

    public void createIssue(int number, String url, String approvedTitle, String approvedBody, List<String> approvedCriteria) {
        if (!"READY".equals(status) || number < 1 || url == null || !url.startsWith("https://github.com/"))
            throw new IllegalStateException("Only a ready issue draft can be published");
        draftTitle = approvedTitle;
        draftBody = approvedBody;
        draftCriteria = String.join("\n", approvedCriteria);
        issueNumber = number;
        issueUrl = url;
        status = "ISSUE_CREATED";
        updatedAt = Instant.now();
    }

    private void requireRunner(String candidate) {
        if (!"RUNNING".equals(status) || runnerId == null || !runnerId.equals(candidate))
            throw new IllegalStateException("Issue conversation is not assigned to this runner");
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
        if (!List.of("anthropic", "openai", "gemini", "local").contains(provider) || model == null || model.isBlank()
                || model.length() > 200 || inputTokens < 0 || outputTokens < 0 || estimatedCostMicros < 0
                || (!costKnown && estimatedCostMicros != 0))
            throw new IllegalArgumentException("Issue conversation usage metadata is invalid");
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getRepository() { return repository; }
    public String getCreatedBy() { return createdBy; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt.toString(); }
    public String getUpdatedAt() { return updatedAt.toString(); }
    public String getStartedAt() { return startedAt == null ? null : startedAt.toString(); }
    public String getFailureSummary() { return failureSummary; }
    public String getDraftTitle() { return draftTitle; }
    public String getDraftBody() { return draftBody; }
    public List<String> getAcceptanceCriteria() { return draftCriteria == null ? List.of() : Arrays.stream(draftCriteria.split("\\n")).filter(value -> !value.isBlank()).toList(); }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getEstimatedCostMicros() { return estimatedCostMicros; }
    public boolean isCostKnown() { return costKnown; }
    public Integer getIssueNumber() { return issueNumber; }
    public String getIssueUrl() { return issueUrl; }
}
