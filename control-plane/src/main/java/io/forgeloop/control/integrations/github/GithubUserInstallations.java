package io.forgeloop.control.integrations.github;

/** Answers whether a GitHub user, identified by their own user access token, can see an App installation. */
public interface GithubUserInstallations {
    /**
     * @return true only when GitHub lists {@code installationId} among the installations accessible to the
     *         token's user; false when it is absent
     * @throws GithubUserTokenRejectedException when GitHub no longer accepts the token (expired or revoked)
     */
    boolean canAccess(String userAccessToken, long installationId);

    /** GitHub refused the user token, so the operator has to sign in again before access can be proven. */
    class GithubUserTokenRejectedException extends IllegalStateException {
        public GithubUserTokenRejectedException() { super("GitHub rejected the signed-in user's access token"); }
    }
}
