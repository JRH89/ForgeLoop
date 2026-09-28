# GitHub user authentication

ForgeLoop uses one GitHub App for two separate trust relationships. Installation tokens authorize repository automation; the GitHub web OAuth flow authenticates human operators. A repository installation never creates an authenticated browser session.

Configure the GitHub App callback URL as `https://forgeloop.hookerhillstudios.com/login/oauth2/code/github`, with wildcard matching, device flow, and authorization-during-installation disabled. Supply `FORGELOOP_GITHUB_CLIENT_ID` and `FORGELOOP_GITHUB_CLIENT_SECRET` through the deployment secret manager. The Client ID is not the App ID.

The public root does not load tenant data. **Sign in with GitHub** redirects through GitHub with OAuth state managed by Spring Security, exchanges the code only on the server, resolves GitHub's immutable numeric user ID, and stores authentication in an HTTP-only, Secure, SameSite cookie. ForgeLoop never uses a GitHub username as an authorization identifier.

Any GitHub account can sign in. On its first sign-in, ForgeLoop creates a separate organization, makes that user its administrator, and seeds conservative execution policy and a generic harness. A transaction-scoped lock keyed to the immutable GitHub ID prevents two simultaneous first logins from creating duplicate workspaces. Existing deployment administrators keep their current organization; this migration does not move their data. Signing in never grants access to another customer's organization.

An organization administrator can invite a teammate from **Team** in the console. Enter their GitHub username and choose `VIEWER`, `OPERATOR`, or `ADMIN`; ForgeLoop resolves the username through GitHub's public user API and stores the immutable numeric ID as `github:<id>`. No invitation email is sent: share the ForgeLoop URL with them. The invitation shows as pending until their first successful GitHub sign-in. Persisted membership, not GitHub profile data, determines access.

Administrators can remove a member or pending invitation from **Team**. Removal revokes subsequent requests and sign-ins, is audit-logged, and allows a later fresh invitation. The final administrator cannot be removed. GitHub user lookup may be rate-limited or unavailable; an invitation fails closed in that case. A GitHub account can belong to multiple ForgeLoop organizations; the console's workspace selector chooses one at a time. The server verifies membership on every switch and scopes the role and all data queries to the selected organization.

Customer organization administrators do not become service-wide support administrators. The support inbox remains restricted to the separately configured service-owner organization (`FORGELOOP_SUPPORT_ADMIN_ORGANIZATION_ID`); a fresh deployment must configure that owner before using the staff inbox.

The self-hosted Compose deployment uses `github` security mode. Hosted production may additionally accept issuer- and audience-validated OIDC bearer tokens for automation, while browser users retain GitHub OAuth sessions. Webhooks continue to use GitHub HMAC signatures and runners continue to use server-validated runner and lease credentials; neither inherits a browser session.

Sign-out invalidates the server session and removes its cookie. Rotating the GitHub client secret requires restarting the control plane but does not change the webhook secret, App private key, or existing installation authorization.
