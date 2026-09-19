package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunnerIdentityTest {
    @Test
    void rejectsMissingCredential() {
        assertThrows(IllegalArgumentException.class, () -> new RunnerIdentity("runner-1", ""));
    }
}
