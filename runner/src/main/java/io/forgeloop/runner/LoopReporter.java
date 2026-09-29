package io.forgeloop.runner;

/** Dispatch-facing seam; tests use an in-memory fake and the runner wires the control plane later. */
public interface LoopReporter {
    void providerAttempt(ProviderAttemptReport report) throws Exception;
    void event(String type, String message) throws Exception;
}
