package io.forgeloop.control.artifacts;

/** Immutable binary storage boundary; implementations must verify bytes after persistence. */
public interface ArtifactStore {
    StoredObject putVerified(String key, byte[] content, String contentType, String sha256);
    byte[] getVerified(String key, String sha256, long maxBytes);
    record StoredObject(long sizeBytes, String sha256) { }
}
