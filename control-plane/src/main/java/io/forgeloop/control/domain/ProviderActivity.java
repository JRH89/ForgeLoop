package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Metadata-only usage from provider calls that do not belong to a delivery task. */
@Entity
@Table(name = "provider_activity")
public class ProviderActivity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false, length = 255) private String organizationId;
    @Column(nullable = false, length = 255) private String repository;
    @Column(nullable = false, length = 32) private String activityType;
    @Column(nullable = false, length = 200) private String sourceId;
    @Column(nullable = false, length = 80) private String provider;
    @Column(nullable = false, length = 200) private String model;
    @Column(nullable = false) private long inputTokens;
    @Column(nullable = false) private long outputTokens;
    @Column(nullable = false) private long estimatedCostMicros;
    @Column(nullable = false) private boolean costKnown;
    @Column(nullable = false) private Instant recordedAt;

    protected ProviderActivity() { }

    public ProviderActivity(String organizationId, String repository, String activityType, String sourceId,
                            String provider, String model, long inputTokens, long outputTokens,
                            long estimatedCostMicros, boolean costKnown, Instant recordedAt) {
        this.organizationId = organizationId;
        this.repository = repository;
        this.activityType = activityType;
        this.sourceId = sourceId;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCostMicros = estimatedCostMicros;
        this.costKnown = costKnown;
        this.recordedAt = recordedAt;
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getRepository() { return repository; }
    public String getActivityType() { return activityType; }
    public String getSourceId() { return sourceId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getEstimatedCostMicros() { return estimatedCostMicros; }
    public boolean isCostKnown() { return costKnown; }
    public Instant getRecordedAt() { return recordedAt; }
}
