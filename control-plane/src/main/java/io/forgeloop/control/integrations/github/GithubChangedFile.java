package io.forgeloop.control.integrations.github;

import java.util.Locale;
import java.util.Set;

/** One file returned by GitHub's compare endpoint, retaining the remote blob identity. */
public record GithubChangedFile(String path, String status, String blobSha) {
    private static final Set<String> STATUSES = Set.of("added", "removed", "modified", "renamed", "changed", "copied");

    public GithubChangedFile {
        if (!safePath(path)) throw new IllegalArgumentException("GitHub compare returned an invalid file path");
        status = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("GitHub compare returned an unsupported file status");
        if ("removed".equals(status)) {
            if (blobSha != null && !blobSha.isBlank() && !validSha(blobSha)) {
                throw new IllegalArgumentException("GitHub compare returned an invalid blob SHA");
            }
        } else if (!validSha(blobSha)) {
            throw new IllegalArgumentException("GitHub compare returned an invalid blob SHA");
        }
    }

    private static boolean validSha(String value) {
        return value != null && value.matches("[0-9a-f]{40,64}");
    }

    private static boolean safePath(String value) {
        if (value == null || value.isBlank() || value.length() > 1000 || value.startsWith("/")
                || value.contains("\\") || value.codePoints().anyMatch(Character::isISOControl)) return false;
        return java.util.Arrays.stream(value.split("/", -1))
                .noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."));
    }
}
