package io.forgeloop.harness;
public record VerificationResult(String gate, boolean passed, String evidencePath, String owner) { public VerificationResult { if(gate.isBlank()||evidencePath.isBlank()||owner.isBlank())throw new IllegalArgumentException("Verification evidence is required"); } }
