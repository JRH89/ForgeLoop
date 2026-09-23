package io.forgeloop.runner;

/** Strict independent-review decision and its provider telemetry. */
public record ReviewResult(boolean approved, String summary, java.util.List<CriterionReview> criteria, ProviderUsageEvidence usage) { }
