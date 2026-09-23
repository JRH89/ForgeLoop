package io.forgeloop.control.application;

import java.util.List;

/** Strict runner submission for one independent review decision. */
public record ReviewEvidenceSubmission(boolean approved, String summary, List<ReviewCriterionSubmission> criteria) { }
