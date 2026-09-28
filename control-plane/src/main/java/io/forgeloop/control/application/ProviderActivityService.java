package io.forgeloop.control.application;

import io.forgeloop.control.domain.ProviderActivity;
import io.forgeloop.control.domain.ProviderActivityRepository;
import io.forgeloop.control.security.OperatorContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped, idempotent provider metering for non-run AI work; never stores prompts or completions. */
@Service
public class ProviderActivityService {
    private static final Set<String> TYPES = Set.of("REPOSITORY_SCAN", "ISSUE_SPECIFICATION", "AI_CHAT");
    private static final Set<String> PROVIDERS = Set.of("anthropic", "openai", "gemini", "local");
    private final ProviderActivityRepository activities;
    private final OperatorContext operators;

    public ProviderActivityService(ProviderActivityRepository activities, OperatorContext operators) {
        this.activities = activities;
        this.operators = operators;
    }

    @Transactional(readOnly = true)
    public List<ProviderActivity> list(int days, String repository) {
        if (!Set.of(7, 30, 90).contains(days)) throw new IllegalArgumentException("Usage period must be 7, 30, or 90 days");
        return activities.findUsage(operators.organizationId(), Instant.now().minus(Duration.ofDays(days)), repository, Pageable.unpaged());
    }

    /** Records one billable activity once. Call within the same transaction that completes its work item. */
    @Transactional
    public ProviderActivity record(String organizationId, String activityType, String repository, String sourceId, String provider, String model,
                                   long inputTokens, long outputTokens, long estimatedCostMicros, boolean costKnown) {
        if (organizationId == null || organizationId.isBlank() || organizationId.length() > 255
                || !TYPES.contains(activityType) || repository == null || repository.isBlank() || repository.length() > 255
                || sourceId == null || sourceId.isBlank() || sourceId.length() > 200
                || provider == null || !PROVIDERS.contains(provider) || model == null || model.isBlank() || model.length() > 200
                || inputTokens < 0 || outputTokens < 0 || estimatedCostMicros < 0 || (!costKnown && estimatedCostMicros != 0))
            throw new IllegalArgumentException("Provider activity metadata is invalid");

        return activities.findByOrganizationIdAndActivityTypeAndSourceId(organizationId, activityType, sourceId)
                .orElseGet(() -> activities.save(new ProviderActivity(organizationId, repository, activityType, sourceId,
                        provider, model, inputTokens, outputTokens, estimatedCostMicros, costKnown, Instant.now())));
    }
}
