package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;

/** Stores one lease nonce locally; completion removes the file in a later execution phase. */
public final class RunnerLeaseStore {
    public void save(Path path, RunnerLease lease) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(path, List.of(lease.leaseId(), lease.nonce()), StandardOpenOption.CREATE_NEW);
        restrictToOwner(path);
    }

    public RunnerLease load(Path path) throws IOException {
        List<String> values = Files.readAllLines(path);
        if (values.size() != 2) throw new IllegalStateException("Runner lease state file is malformed");
        return new RunnerLease(values.get(0), values.get(1));
    }

    /** Lease nonces are bearer material and receive the same owner-only treatment as runner credentials. */
    private void restrictToOwner(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows ACL policy remains under the administrator's control.
        }
    }
}
