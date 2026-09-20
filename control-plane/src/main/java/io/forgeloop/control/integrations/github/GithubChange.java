package io.forgeloop.control.integrations.github;

/** A path-restricted repository change prepared by a runner after verification. */
public record GithubChange(String path, String content, String message) {
    public GithubChange {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..") || content == null) {
            throw new IllegalArgumentException("GitHub change path is unsafe");
        }
    }
}
