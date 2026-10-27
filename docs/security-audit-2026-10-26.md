# Security audit — October 26, 2026

This audit reviewed the control plane's authentication, tenant authorization, GitHub integration, upload
and download paths, rate limiting, the runner's repository sandbox, the web gateway, deployment
configuration, and all three dependency trees. It follows the original outline's tenant-isolation and
bounded-execution requirements. No committed secrets were found in the tree or in Git history.

## Findings fixed

| Severity | Area | Finding | Fix |
| --- | --- | --- | --- |
| High | GitHub App setup | `GET /api/github/app/callback` trusted the `installation_id` query parameter. The signed state proved which tenant started an installation, but not which installation it was. A member of any tenant (including a viewer) could mint a state and bind another customer's unclaimed installation, such as one created from GitHub directly, to their own tenant, along with its repositories. | Only administrators can start an installation. The state now binds the tenant *and* the initiating operator. The callback requires that same GitHub browser session and calls `GET /user/installations` with the operator's own token to prove access before anything is persisted (`GithubInstallationOwnershipVerifier`). |
| High | Runner sandbox (Windows) | The worktree path guard blocked `.git` but not the spellings Win32 resolves to it (`.git.`, `.git `, `GIT~1`, `.git::$INDEX_ALLOCATION`). In a worktree, `.git` is a gitfile, so overwriting it could point Git at an agent-written configuration that runs commands on the runner host. | `WorktreePathGuard` canonicalizes each segment the way Git's `core.protectNTFS` does, on every platform. |
| Medium | Runner Git | Runner Git commands honoured repository hooks and `core.fsmonitor`, so repository state could execute code during `add`, `commit` and `cherry-pick`. | All Git invocations share one hardened prefix: `core.hooksPath=/dev/null` and `core.fsmonitor=false`, in addition to `safe.directory`. |
| Medium | Authorization | `submitFeature` had no role check, so a read-only viewer could start runs that spend the organization's provider budget. Its duplicate-source check also ran before the tenant check, which revealed whether another tenant had a run. | Requires the operator role and checks tenant access first. |
| Medium | Session cookie | The cookie settings lived under `spring.session.cookie`, which applies only to Spring Session. That dependency is not present, so `SameSite=Lax` was never sent, even though CSRF tokens are disabled. | Moved to `server.servlet.session.cookie`. `Secure` follows the trusted proxy's scheme unless `FORGELOOP_SESSION_COOKIE_SECURE=true` forces it. |
| Medium | Rate limiting | The limiter keyed requests on the unauthenticated `X-ForgeLoop-Runner-Id` header on every route. Rotating it bypassed the limit, and it could fill the 10,000-entry window table so that all other clients received 429s. | Every request is charged to its peer address first. Runner routes get a larger per-peer allowance (for NAT) and a separate window per well-formed runner ID. |
| Medium | Support rate limiting | The gateway replaced `X-Forwarded-For` with the internal edge proxy's address. As a result, every visitor shared one support bucket, and one person could block guest recovery for everyone. | The gateway appends to the chain. Tomcat's `RemoteIpValve` resolves the Cloudflare-appended client address and skips only private-network proxies. |
| Low | Web gateway | Static responses had no clickjacking, MIME-sniffing, referrer or transport headers, including `/app/runner-connect`, where administrators approve runner pairing. | `frontend/nginx-security-headers.conf` is included in every static location. Proxied API routes keep Spring Security's equivalent headers. |
| Medium | GraphQL cost | `/graphql` is reachable without a session, and nothing bounded a single document's cost. Aliases could repeat an expensive field such as `featureRuns(size: 50)` hundreds of times in one request. Schema introspection was also public. | `GraphQlLimitsConfiguration` caps depth at 10 and complexity at 300. Every operation sent by the console, MCP gateway and runner (74 in total) was measured: the maximum is depth 4 and 79 fields. Introspection is off unless `FORGELOOP_GRAPHQL_INTROSPECTION=true`. |
| Low | Webhook retries | The delivery ID was stored before processing. If processing failed, GitHub's redelivery was skipped as a duplicate, and two concurrent copies of one delivery caused a 500. | Claim-and-release: the ID is inserted immediately as an idempotency lock and released if processing fails, so a redelivery is processed. A concurrent duplicate that loses the unique-key race is acknowledged. |
| Low | Sign-out | `/logout` accepted GET, so any page could sign an operator out with an image or link. | The control plane accepts only POST (the SameSite cookie keeps cross-site POSTs anonymous). Each "Sign out" control in the console, account page and support page is now a `SignOutButton` form. |
| Low | Install callback | A forged or expired callback state returned HTTP 500. | Returns 400 without echoing details. |
| Low | Artifact download | Runner-produced bytes are served inline on the operator origin. | Responses carry `Content-Security-Policy: sandbox; default-src 'none'` and `nosniff`. |
| Low | Dependencies | `undici` (frontend dev tooling, high) and `fast-uri` (MCP server, moderate) had published advisories. | Lockfiles updated. `npm audit` reports zero vulnerabilities in both trees. |

## Reviewed and found sound

- GitHub webhooks: HMAC-SHA256 with a constant-time comparison, and idempotent delivery IDs.
- Runner credentials and registration tokens: `SecureRandom` values that are stored only as SHA-256 hashes.
- Tenant scoping in the GraphQL services: run, repository, scan, proposal, conversation, escalation,
  membership, MCP and policy operations each check the organization and role in the service layer.
- Support API: same-origin custom-header CSRF boundary, `Origin` validation, and bounded bodies.
- Local MCP commands: the control plane can only name commands that the runner allowlists locally.
- Frontend: no raw HTML sinks. React 19 neutralizes `javascript:` URLs, and issue links come from GitHub
  API receipts.
- Compose: every host port is bound to loopback, and the tunnel catch-all returns 404.

## Follow-up

All findings from this audit, including the three first listed as recommendations, are fixed. None remain open.

## Verification

- Control plane: `mvn verify`, 406 tests, 0 failures. These include real-schema tests of the GraphQL limits, real-filter-chain tests of POST-only sign-out, and webhook retry and concurrency tests. A live boot against PostgreSQL confirmed `SameSite=Lax` (and `Secure` behind HTTPS) on the session cookie. It also confirmed that forged callbacks get a 400 and that a correctly signed callback without a GitHub session claims nothing.
- Runner: `mvn verify`, 395 tests, 0 failures. Ten tests are environment-gated skips (desktop GUI,
  Docker-in-Docker, live provider fixtures). A real-repository test proves that hooks configured both in
  `.git/hooks` and through `core.hooksPath` do not run.
- Frontend: lint, 101 unit tests, typecheck, production build, prerender and SEO checks. The full
  Playwright suite (40 tests) passed against the built site served by the production nginx config and a
  development-mode control plane on PostgreSQL. One layout-position assertion failed once on the public
  pages and passed on two full reruns; those pages were not changed.
- MCP server: tests and typecheck.
- Gateway: `nginx -t` on `nginx:1.29-alpine`, plus a served-response check of the headers on public,
  support, asset, pairing and 404 routes. `scripts/Test-ComposeSecurity.mjs` passes.
