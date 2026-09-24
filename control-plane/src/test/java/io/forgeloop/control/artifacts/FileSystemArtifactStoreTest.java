package io.forgeloop.control.artifacts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemArtifactStoreTest {
    @TempDir Path root;

    @Test void persistsAndVerifiesChecksummedContent() throws Exception {
        byte[] content = "{\"passed\":true}".getBytes(StandardCharsets.UTF_8);
        String digest = ArtifactDigests.sha256(content);

        ArtifactStore.StoredObject stored = new FileSystemArtifactStore(root)
                .putVerified("org/run/task/lease.json", content, "application/json", digest);

        assertEquals(content.length, stored.sizeBytes());
        assertEquals(digest, stored.sha256());
        assertArrayEquals(content, Files.readAllBytes(root.resolve("org/run/task/lease.json")));
    }

    @Test void rejectsKeysOutsideStorageRoot() {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> new FileSystemArtifactStore(root)
                .putVerified("../escaped.json", content, "application/json", ArtifactDigests.sha256(content)));
    }

    @Test void retrievesOnlyContentMatchingItsPersistedChecksum() throws Exception {
        byte[] content = "image".getBytes(StandardCharsets.UTF_8);
        String digest = ArtifactDigests.sha256(content);
        FileSystemArtifactStore store = new FileSystemArtifactStore(root);
        store.putVerified("org/run/screen.png", content, "image/png", digest);

        assertArrayEquals(content, store.getVerified("org/run/screen.png", digest, 1024));
        assertThrows(IllegalStateException.class,
                () -> store.getVerified("org/run/screen.png", "0".repeat(64), 1024));
    }

    @Test void rejectsDifferentContentAtAnImmutableKeyWithoutReplacingIt() throws Exception {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        Path target = root.resolve("org/run/bad.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "different");

        assertThrows(IllegalStateException.class, () -> new FileSystemArtifactStore(root)
                .putVerified("org/run/bad.json", content, "application/json", ArtifactDigests.sha256(content)));
        assertEquals("different", Files.readString(target));
    }
}
