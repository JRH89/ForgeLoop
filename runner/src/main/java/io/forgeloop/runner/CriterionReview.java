package io.forgeloop.runner;

/** One strict acceptance-criterion outcome from the independent reviewer. */
public record CriterionReview(String statement, String status, String evidence) { }
