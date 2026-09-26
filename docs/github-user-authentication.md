# GitHub user authentication

ForgeLoop uses one GitHub App for two separate trust relationships. Installation tokens authorize repository automation; the GitHub web OAuth flow authenticates human operators. A repository installation never creates an authenticated browser session.

Configure the GitHub App callback URL as `https://forgeloop.hookerhillstudios.com/login/oauth2/code/github`, with wildcard matching, device flow, and authorization-during-installation disabled. Supply `FORGELOOP_GITHUB_CLIENT_ID` and `FORGELOOP_GITHUB_CLIENT_SECRET` through the deployment secret manager. The Client ID is not the App ID.

The public root does not load tenant data. **Sign in with GitHub** redirects through GitHub with OAuth state managed by Spring Security, exchanges the code only on the server, resolves GitHub's immutable numeric user ID, and stores authentication in an HTTP-only, Secure, SameSite cookie. ForgeLoop never uses a GitHub username as an authorization identifier.

On an empty installation, the first authenticated GitHub identity becomes administrator of the configured bootstrap organization. The organization row is pessimistically locked so two concurrent first logins cannot both bootstrap access. After bootstrap, an unknown GitHub identity is rejected until an existing administrator grants membership to its `github:<numeric-id>` subject. Persisted membership, not GitHub profile data, determines `ADMIN`, `OPERATOR`, or `VIEWER` access.

The self-hosted Compose deployment uses `github` security mode. Hosted production may additionally accept issuer- and audience-validated OIDC bearer tokens for automation, while browser users retain GitHub OAuth sessions. Webhooks continue to use GitHub HMAC signatures and runners continue to use server-validated runner and lease credentials; neither inherits a browser session.

Sign-out invalidates the server session and removes its cookie. Rotating the GitHub client secret requires restarting the control plane but does not change the webhook secret, App private key, or existing installation authorization.
