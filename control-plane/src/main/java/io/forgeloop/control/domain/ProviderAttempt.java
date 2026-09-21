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

/** Append-only, redacted provider telemetry bound to the runner lease that produced it. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "request_id_digest"}))
public class ProviderAttempt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private DeliveryTask task;
    @ManyToOne(optional = false) private Runner runner;
    @Column(nullable = false, length = 40) private String provider;
    @Column(nullable = false, length = 255) private String model;
    @Column(nullable = false, length = 64) private String requestIdDigest;
    private long inputTokens;
    private long outputTokens;
    private int attemptCount;
    private long estimatedCostMicros;
    private boolean costKnown;
    @Column(nullable = false, length = 40) private String outcome;
    private boolean retryable;
    @Column(nullable = false, length = 80) private String category;
    @Column(nullable = false) private Instant recordedAt;

    protected ProviderAttempt() { }

    public ProviderAttempt(DeliveryTask task, Runner runner, String provider, String model, String requestIdDigest,
                           long inputTokens, long outputTokens, int attemptCount, long estimatedCostMicros,
                           boolean costKnown, String outcome, boolean retryable, String category) {
        this.task = task; this.runner = runner; this.provider = provider; this.model = model;
        this.requestIdDigest = requestIdDigest; this.inputTokens = inputTokens; this.outputTokens = outputTokens;
        this.attemptCount = attemptCount; this.estimatedCostMicros = estimatedCostMicros; this.costKnown = costKnown;
        this.outcome = outcome; this.retryable = retryable;
        this.category = category; this.recordedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTaskId() { return task.getId(); }
    public String getRunnerId() { return runner.getId(); }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getRequestIdDigest() { return requestIdDigest; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public int getAttemptCount() { return attemptCount; }
    public long getEstimatedCostMicros() { return estimatedCostMicros; }
    public boolean isCostKnown() { return costKnown; }
    public String getOutcome() { return outcome; }
    public boolean isRetryable() { return retryable; }
    public String getCategory() { return category; }
    public String getRecordedAt() { return recordedAt.toString(); }
}
