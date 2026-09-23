package io.forgeloop.control.artifacts;

import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Development storage backend with atomic writes and mandatory read-after-write verification. */
final class FileSystemArtifactStore implements ArtifactStore {
    private final Path root;
    FileSystemArtifactStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    @Override public StoredObject putVerified(String key, byte[] content, String contentType, String sha256) {
        try {
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Artifact key escapes its storage root");
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".artifact-", ".tmp");
            try {
                Files.write(temporary, content);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) {
                    // Some mounted development volumes do not offer atomic renames. A same-directory
                    // replacement still prevents partially written target files from being observed.
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            finally { Files.deleteIfExists(temporary); }
            byte[] persisted = Files.readAllBytes(target);
            String actual = ArtifactDigests.sha256(persisted);
            if (persisted.length != content.length || !actual.equals(sha256)) { Files.deleteIfExists(target); throw new IllegalStateException("Artifact failed read-after-write verification"); }
            return new StoredObject(persisted.length, actual);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Artifact storage failed", failure); }
    }
}
