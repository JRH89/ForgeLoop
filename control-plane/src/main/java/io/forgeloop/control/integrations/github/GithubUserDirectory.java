package io.forgeloop.control.integrations.github;

/** Resolves a human-entered GitHub login to the immutable account identifier. */
public interface GithubUserDirectory {
    GithubUser findByLogin(String login);

    record GithubUser(String login, long id) { }
}
