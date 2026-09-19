package io.forgeloop.runner;

/** Runner-scoped credential issued once during enrollment and retained locally. */
public record RunnerIdentity(String runnerId, String credential) {
    public RunnerIdentity { if (runnerId == null || runnerId.isBlank() || credential == null || credential.isBlank()) throw new IllegalArgumentException("Runner identity is incomplete"); }
}
