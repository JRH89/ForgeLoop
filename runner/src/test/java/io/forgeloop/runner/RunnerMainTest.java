package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunnerMainTest {
    @Test
    void parsesExplicitRegistrationArguments() {
        RunnerConfig config = RunnerMain.configFrom(new String[]{
                "register", "http://localhost:8090", "registration-token", "build-node", "git,docker"
        });

        assertEquals("build-node", config.name());
        assertEquals(2, config.capabilities().size());
    }

    @Test
    void rejectsIncompleteRegistrationArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> RunnerMain.configFrom(new String[]{"register", "http://localhost:8090", "token", "node"}));
    }

    @Test
    void extractsRunnerIdFromGraphQlResponse() {
        assertEquals("runner-123", RunnerMain.runnerIdFrom("{\"data\":{\"registerRunner\":{\"id\":\"runner-123\"}}}"));
    }
}
