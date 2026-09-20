package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerLeaseStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void storesLeaseNonceWithOwnerOnlyPermissionsWhereSupported() throws Exception {
        Path state = temporaryDirectory.resolve("state/lease");
        RunnerLease lease = new RunnerLease("lease-1", "nonce-secret");
        RunnerLeaseStore store = new RunnerLeaseStore();

        store.save(state, lease);

        assertEquals(lease, store.load(state));
        if (Files.getFileAttributeView(state, PosixFileAttributeView.class) != null) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(state));
        }
    }
}
