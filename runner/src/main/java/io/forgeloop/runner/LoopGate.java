package io.forgeloop.runner;

import java.util.List;

/** Advisory task gate available to run_gate; it is not official verification evidence. */
public record LoopGate(String name, String image, List<String> command, int timeoutSeconds, boolean allowNetwork) {
    public LoopGate {
        if (name == null || name.isBlank() || image == null || image.isBlank() || command == null || command.isEmpty()
                || command.stream().anyMatch(value -> value == null || value.isBlank()) || timeoutSeconds < 1 || timeoutSeconds > 3_600)
            throw new IllegalArgumentException("Agent loop gate is invalid");
        command = List.copyOf(command);
    }
}
