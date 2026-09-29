package io.forgeloop.runner;

/** One bounded, non-secret diagnostic emitted by the offline run-record verifier. */
public record VerifyCheck(String id, String subject, VerifyVerdict verdict, String detail) { }
