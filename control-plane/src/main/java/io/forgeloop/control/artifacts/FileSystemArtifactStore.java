package io.forgeloop.control.artifacts;

import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
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
            if (Files.exists(target)) return verifyExisting(target, content.length, sha256);
            Path temporary = Files.createTempFile(target.getParent(), ".artifact-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException unsupported) {
                        // Same-directory moves keep partially written temporary files invisible even
                        // on development mounts that cannot provide an atomic rename.
                        Files.move(temporary, target);
                    }
                }
                catch (FileAlreadyExistsException raced) { return verifyExisting(target, content.length, sha256); }
            }
            finally { Files.deleteIfExists(temporary); }
            return verifyExisting(target, content.length, sha256);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Artifact storage failed", failure); }
    }

    @Override public byte[] getVerified(String key, String sha256, long maxBytes) {
        try {
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target) || Files.size(target) > maxBytes) {
                throw new IllegalArgumentException("Artifact is unavailable");
            }
            byte[] content = Files.readAllBytes(target);
            if (!ArtifactDigests.sha256(content).equals(sha256)) throw new IllegalStateException("Stored artifact checksum mismatch");
            return content;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Artifact retrieval failed", failure); }
    }

    private static StoredObject verifyExisting(Path target, int expectedLength, String expectedSha256) throws java.io.IOException {
        byte[] persisted = Files.readAllBytes(target);
        String actual = ArtifactDigests.sha256(persisted);
        if (persisted.length != expectedLength || !actual.equals(expectedSha256)) {
            throw new IllegalStateException("Immutable artifact key already contains different or corrupt content");
        }
        return new StoredObject(persisted.length, actual);
    }
}
