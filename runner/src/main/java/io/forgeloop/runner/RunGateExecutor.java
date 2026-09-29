package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/** Injectable advisory-gate runner. Production container wiring is added in the dispatch slice. */
@FunctionalInterface
public interface RunGateExecutor {
    VerificationResult execute(LoopGate gate, Path worktree, Duration timeout) throws IOException, InterruptedException;
}
