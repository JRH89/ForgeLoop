package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Stores one lease nonce locally; completion removes the file in a later execution phase. */
public final class RunnerLeaseStore {
    public void save(Path path, RunnerLease lease) throws IOException {
        Files.write(path, List.of(lease.leaseId(), lease.nonce()), StandardOpenOption.CREATE_NEW);
    }

    public RunnerLease load(Path path) throws IOException {
        List<String> values = Files.readAllLines(path);
        if (values.size() != 2) throw new IllegalStateException("Runner lease state file is malformed");
        return new RunnerLease(values.get(0), values.get(1));
    }
}
