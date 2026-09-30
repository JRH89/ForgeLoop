package io.forgeloop.control.integrations.github;

import java.util.Optional;

/** Parses the deliberately narrow issue-origin run reference into a safe GitHub issue number. */
record GithubSourceIssue(int number) {
    static Optional<GithubSourceIssue> fromSourceRef(String sourceRef) {
        if (sourceRef == null || !sourceRef.matches("issue-[1-9][0-9]*")) return Optional.empty();
        try { return Optional.of(new GithubSourceIssue(Integer.parseInt(sourceRef.substring("issue-".length())))); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }
}
