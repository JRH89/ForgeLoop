package io.forgeloop.control.application;

/** One reviewer assessment bound to an acceptance criterion copied from the run. */
public record ReviewCriterionSubmission(String statement, String status, String evidence) { }
