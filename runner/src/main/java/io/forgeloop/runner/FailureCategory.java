package io.forgeloop.runner;

/** Normalized, model-visible tool failure categories. Harness failures never use this envelope. */
public enum FailureCategory { TRANSIENT, VALIDATION, BUSINESS_RULE, PERMISSION }
