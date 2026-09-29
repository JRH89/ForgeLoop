package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoopInstructionsTest {
    @Test
    void keepsOneRoleNeutralSafetyAndCompletionContract() {
        String instructions = LoopInstructions.text();
        assertTrue(instructions.contains("Use only the tools provided"));
        assertTrue(instructions.contains("Write only within the owned paths"));
        assertTrue(instructions.contains("call finish"));
        assertTrue(instructions.contains("untrusted data"));
        assertFalse(instructions.contains("IMPLEMENTATION"));
        assertFalse(instructions.contains("REPAIR"));
    }
}
