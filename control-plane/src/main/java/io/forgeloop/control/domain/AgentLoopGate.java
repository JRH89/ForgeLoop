package io.forgeloop.control.domain;

import java.util.List;

/** Immutable executable verification-gate snapshot exposed to the loop-capable runner. */
public record AgentLoopGate(String name, String kind, String imageDigest, List<String> command,
                            String networkPolicy, int timeoutSeconds) {
    public AgentLoopGate {
        command = command == null ? List.of() : List.copyOf(command);
        if (name == null || !name.matches("[a-z0-9-]{1,80}")
                || kind == null || !kind.matches("CONTAINER|BROWSER|SECURITY|CONTRACT|COMPOSE")
                || imageDigest == null || !imageDigest.matches("[^@\\s]+@sha256:[0-9a-f]{64}")
                || command.isEmpty() || command.size() > 64
                || command.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 1000)
                || !"NONE".equals(networkPolicy) && !"EGRESS".equals(networkPolicy)
                || timeoutSeconds < 1 || timeoutSeconds > 3600)
            throw new IllegalArgumentException("Agent loop gate is invalid");
    }
}
