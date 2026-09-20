package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunnerResultParsingTest {
    @Test
    void rejectsAmbiguousLeaseCompletionResult() {
        assertThrows(IllegalArgumentException.class,
                () -> RunnerMain.main(new String[]{"complete-lease", "http://localhost:8090", "state", "lease", "nonce", "pass"}));
    }
}
