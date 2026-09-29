package io.forgeloop.runner;

/** Runner-local post-image retained for deterministic journal replay. */
public record PostImage(String path, String sha256, String content) {
    public PostImage {
        if (path == null || path.isBlank() || sha256 == null || !sha256.matches("[0-9a-f]{64}") || content == null)
            throw new IllegalArgumentException("Tool post-image is invalid");
    }
}
