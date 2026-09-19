package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Stores runner-only enrollment material in an explicitly mounted local path. */
public final class RunnerIdentityStore {
    public void save(Path path, RunnerIdentity identity) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(path, List.of(identity.runnerId(), identity.credential()), StandardOpenOption.CREATE_NEW);
    }

    public RunnerIdentity load(Path path) throws IOException {
        List<String> values = Files.readAllLines(path);
        if (values.size() != 2) throw new IllegalStateException("Runner state file is malformed");
        return new RunnerIdentity(values.get(0), values.get(1));
    }
}
