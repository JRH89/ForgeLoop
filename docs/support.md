# Customer support

## Customers

Open **Contact** (`/contact`) and submit your name, email, category, subject,
and a description. An account is not required. Never submit credentials,
payment details, or sensitive repository contents. Attachments are not supported.

After submission, save the **private tracking link**. It opens the ticket,
its status history, and the conversation, and lets you reply. Anyone with that
link has the same access: treat it like a password. The key is returned once;
the database stores only its SHA-256 digest. The browser keeps it in this tab's
session storage and removes it from the address bar when storage succeeds.
Opening the saved link works in another browser. If storage is unavailable,
the key remains in the URL fragment so refreshing still works.

Signed-in customers also find tickets they submitted while signed in under
**My tickets** (`/support#mine`). Ownership is the immutable authenticated
identity, not the submitted email. Signing in later does not automatically
claim guest tickets. If recovery is enabled, a guest ticket can be reclaimed
only by redeeming a one-time verification link sent to its submitted email;
ticket reference or email alone never grants access.

Ticket receipts and replies are not sent by email. If guest-link recovery is
enabled for the deployment, open **Support** and use **Recover a guest ticket**
with the ticket reference and submitted email. The same confirmation appears
whether those details match or not. A matching guest ticket receives a
single-use verification link that expires after 30 minutes. Opening it verifies
the submitted email, creates a replacement private link, and invalidates both
the previous ticket link and any older recovery links. A ticket can send at
most three recovery messages per hour; the support API also applies a stricter
per-peer request limit. The email page requires a deliberate confirmation click,
so link-preview scanners do not consume the token. Signed-in tickets are not
recovered through this flow.
Return to the private link to check replies. Threads and inboxes refresh every
15 seconds while visible. No support action starts a runner or paid model call.
Support response times are not guaranteed.

## Service owner / administrators

Visit `/support#admin` and choose **Sign in as administrator**, or use
`/api/support/login?admin=true` to sign in with the existing GitHub App.
The OAuth callback returns to the support inbox. Existing account invitations
and GitHub login restrictions still apply.

Only an **ADMIN membership in the service-owner organization** configured by
`FORGELOOP_SUPPORT_ADMIN_ORGANIZATION_ID` grants global support access. Compose
defaults this to `local-development`. Set an explicit service-owner organization
for other deployments. A customer organization's admin role never grants global
support access. Anonymous development-console access does not grant it either.

The inbox supports status filtering, literal text search (subject, submitted
email, or ticket ID), and pages of 25. Open a ticket to reply, change status, or
add a **private internal note**. Internal notes are omitted from customer API
responses, not merely hidden in the browser. Submitted names/emails are not
verified identities. Staff author identities are retained in database audit
records; customer responses show only the author role.

| Status | Meaning |
| --- | --- |
| Open | New request, or customer replied to a waiting/resolved request |
| In progress | Staff are working on it |
| Waiting on customer | More information is needed |
| Resolved | Answered; a customer reply reopens it |
| Closed | Replies disabled; staff can change status or customer can open a new ticket |

Status changes appear in the conversation. Concurrent writes use a locked row
and version check: a stale update returns 409, and the browser retains the reply
draft. Wait for refresh before resubmitting. Closed threads are read-only unless
staff change their status. Threads allow at most 200 messages including status
events; open a follow-up when full.

## Operations and security

Flyway V28 adds `support_ticket` and `support_message`; V30 adds the nullable
email-verification timestamp and `support_recovery` table. These migrations do
not alter run data. Existing PostgreSQL backup/restore procedures include the
support tables. Take a verified backup before upgrading. Support contains personal information: limit
database/backup access and define the deployment's retention policy. No automatic
deletion, retention schedule, or attachment storage is configured. Old recovery
records are purged opportunistically after 30 days when a valid recovery request
is processed.

### Enable guest recovery email

Recovery is **disabled by default**. Before enabling it, configure a dedicated
SMTP sender (with TLS and provider-side sending limits), set a canonical HTTPS
`FORGELOOP_PUBLIC_BASE_URL`, and verify the From address with the mail provider.
Inject `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`,
`SPRING_MAIL_PASSWORD`, `FORGELOOP_SUPPORT_EMAIL_FROM`, and
`FORGELOOP_PUBLIC_BASE_URL` from the deployment's secret manager/environment.
Then set `FORGELOOP_SUPPORT_RECOVERY_ENABLED=true` and restart the control plane.
The portal exposes the form only when all required configuration is present.

Recovery email contains no ticket subject or conversation content. Its token is
high entropy, stored only as a SHA-256 digest, delivered only after the recovery
record commits, and never written to application logs. The link carries the
token in a URL fragment; browser code submits it in a same-origin POST body.
Tokens are single-use and expire after 30 minutes. Successful redemption marks
the requester's email verified and rotates the ticket capability, immediately
revoking the old link. SMTP delivery failures are logged without the address or
token; they do not reveal ticket existence to the requester. Monitor those
errors and test the complete request/verify flow with a controlled mailbox
before enabling recovery in production. No message or secret is sent through
the ticket itself.

`/support` and its API responses are `no-store` and `noindex`; the portal is not
in the sitemap. Private keys use URL fragments and a request header, never query
parameters. Do not log request headers/bodies or enable analytics that capture
support fragments, form content, or replies.

Every API operation checks ticket ownership, capability, or staff authorization.
Same-origin custom headers and Origin/Fetch Metadata checks protect writes;
cross-origin CORS access is not enabled. JSON bodies are capped at 32 KiB by both
Nginx and the application. Individual messages are limited to 8,000 characters.
A honeypot and process-local per-peer minute limits (10 submissions, 5 recovery
email requests, 10 recovery confirmations, 30 other writes, 240 reads) reduce
abuse. Recovery also allows at most three emails per ticket in a rolling hour.
These are not a distributed anti-abuse service:
proxy peers may share a limit, and replicas have independent counters. Configure
trusted edge rate limits/WAF controls before high-volume public operation. Do
not blindly trust client-supplied forwarding headers for abuse identity.

## Verification

- `docker build -t forgeloop-support-control-test control-plane`: backend tests.
- `cd frontend; npm.cmd run check`: frontend tests, lint, types, build, SEO.
- Against a **disposable** migrated database/web stack only, set
  `FORGELOOP_SUPPORT_E2E=1` and `PLAYWRIGHT_BASE_URL` before running
  `npm.cmd run test:e2e -- e2e/support.spec.ts`. These tests create support tickets.
  CI enables them only in its disposable Compose stack.
- Backend tests cover guest capabilities, identity isolation, staff authorization,
  private notes, statuses, stale writes, input validation, pagination, and request
  bounds. Recovery tests cover generic responses, guest-only eligibility, per-ticket
  throttling, token supersession/expiry/replay, address verification, capability
  rotation, and after-commit delivery. Frontend tests cover the recovery request
  and deliberate one-time-link redemption. Browser tests cover submission,
  cross-browser tracking, refresh, replies, denied access, mobile layout, and
  accessibility. A real SMTP delivery check still requires deployment mail
  credentials and a controlled mailbox; do not enable recovery before that smoke
  test succeeds.
