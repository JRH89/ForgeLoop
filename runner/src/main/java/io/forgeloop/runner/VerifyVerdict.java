package io.forgeloop.runner;

/** Three-valued result: unsupported or missing proof is never silently treated as a pass. */
public enum VerifyVerdict { PASS, FAIL, UNVERIFIABLE }
