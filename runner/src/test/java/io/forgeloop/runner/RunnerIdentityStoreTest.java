package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerIdentityStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void roundTripsRunnerCredentialWithoutLoggingIt() throws Exception {
        Path state = temporaryDirectory.resolve("state/runner");
        RunnerIdentity identity = new RunnerIdentity("runner-1", "credential-secret");
        RunnerIdentityStore store = new RunnerIdentityStore();

        store.save(state, identity);

        assertEquals(identity, store.load(state));
    }
}
