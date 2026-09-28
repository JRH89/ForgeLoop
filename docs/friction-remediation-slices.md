# Friction-remediation slices

This plan follows the 2026-09-27 code audit. Each numbered item is exactly one
GitHub issue, one implementation branch, and one pull request linked with a
closing keyword (`Closes #N`). Do not bundle unrelated fixes into a slice. Merge
only after its tests and review pass, then start the next slice from current
`master`. Keep the existing product roadmap and launch gates authoritative;
these slices improve usability and reliability without declaring the platform
production-ready.

| Slice | Outcome | GitHub issue | Pull request | State |
| --- | --- | --- | --- | --- |
| 1 | Reliable isolated PostgreSQL restore drill | [#47](https://github.com/JRH89/ForgeLoop/issues/47) | [#53](https://github.com/JRH89/ForgeLoop/pull/53) | Merged |
| 2 | GitHub self-signup and administrator-managed invitations | [#48](https://github.com/JRH89/ForgeLoop/issues/48) | [#54](https://github.com/JRH89/ForgeLoop/pull/54) | Merged |
| 3 | Trustworthy, guided runner installation | [#49](https://github.com/JRH89/ForgeLoop/issues/49) | [#55](https://github.com/JRH89/ForgeLoop/pull/55) | Merged |
| 4 | Safe desktop runner update experience | [#50](https://github.com/JRH89/ForgeLoop/issues/50) | [#56](https://github.com/JRH89/ForgeLoop/pull/56) | Merged |
| 5 | Clear GitHub issue handoff and intake diagnosis | [#51](https://github.com/JRH89/ForgeLoop/issues/51) | [#57](https://github.com/JRH89/ForgeLoop/pull/57) | In review |
| 6 | Recoverable guest support tracking | [#52](https://github.com/JRH89/ForgeLoop/issues/52) | Pending | Planned |

## Slice 1 — Reliable isolated PostgreSQL restore drill

The restore test must wait for PostgreSQL's final server, not the temporary
initialization server that can briefly satisfy `pg_isready` before shutting
down. Restore into an isolated container without changing live volumes. Test
both a valid backup and a bounded failure/timeout; keep cleanup limited to the
unique drill container and temporary dump. Exit when the end-to-end CI restore
step is repeatable and still validates schema plus operational records.

## Slice 2 — Self-service GitHub signup and administrator-managed invitations

GitHub login currently denies users without persisted membership after the
bootstrap administrator. Any GitHub user must instead be able to sign in and
receive a new isolated organization with safe policy and a generic harness.
Do not add strangers to an existing organization. Separately, give an
administrator an organization-scoped invitation workflow in the console.
Bind invitations to immutable GitHub user IDs, enforce role and tenant
boundaries, handle duplicate and revoked invites, and show useful
acceptance/error states. Exit with concurrent-signup, authorization,
integration, and browser tests plus user/admin documentation.

## Slice 3 — Trustworthy, guided runner installation

Reduce first-run friction around separately installed Git/Docker with clear
platform-specific prerequisite checks and links, actionable failures, and
preflight before pairing or paid work. Keep the preview's unsigned status
explicit. Production signing/notarization needs real publisher credentials and
OS acceptance evidence; do not label an unsigned build trusted or complete this
slice's production-signing gate without those inputs. Test each supported OS
package and document the exact remaining trust boundary.

## Slice 4 — Safe desktop runner update experience

The current app only opens the downloads page; users must manually discover,
download, close, and replace it. Add version comparison, release provenance and
checksum display, a clear pause/finish-work requirement, and a guided update
handoff that preserves local identity, keys, and settings. No installer may run
while work is active. Test current, newer, unavailable, and tampered metadata,
plus cross-version state preservation. OS-specific signed in-app installation
remains gated by slice 3's signing credentials.

## Slice 5 — Clear GitHub issue handoff and intake diagnosis

The draft-issue link currently omits the required label/assignee, and diagnosis
requires manually copying an issue number. Guide users through the explicit
handoff without inadvertently starting paid work. Pre-populate safe draft
metadata where GitHub supports it, make the activation step and consequences
obvious, and accept a pasted issue URL or number for a read-only eligibility
check. Test label/assignee policies, malformed links, duplicate intake, and
mobile/browser flow. No automatic labeling or assignment without confirmation.

## Slice 6 — Recoverable guest support tracking

Guest tickets currently rely on a single bearer link with no confirmation
email. Add a privacy-preserving recovery path, with verified address ownership,
rate limiting, token rotation/expiry policy, and no ticket-content disclosure
to unverified requesters. Maintain authenticated users' existing ticket view.
Test lost-link recovery, enumeration resistance, expired/used tokens, and
cross-user isolation; document support operations and email delivery needs.

## Delivery rules

- Each PR links only its own issue and records tests, security boundaries, user
  documentation, and any release or deployment evidence.
- A preview artifact or passing unit test is not proof of a signed installer,
  funded provider run, off-host restore, or production go/no-go approval.
- Preserve `docs/2026-09-2026-roadmap.md`, which is a separate user-authored
  worktree file and is not part of these slices.
