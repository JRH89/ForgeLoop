# Desktop runner onboarding

Replace the primary terminal-based journey with download, install, browser
approval, local provider setup, and an explicit start. Keep the CLI as an advanced
fallback. Existing runners must not be re-enrolled or migrated implicitly.

## Acceptance slices

- [x] Browser pairing implementation and unit tests: administrator approval bound to a desktop-generated secret,
  short expiry, single-use exchange, organization isolation, no credentials in URLs.
  Disposable-stack integration passed locally and in CI, including actual
  PostgreSQL persistence, enrollment heartbeat, and replay rejection.
- [x] Desktop setup: bundled Java, prerequisite checks, secure native credential
  storage, provider/model selection and editable dated price estimates.
- [x] Lifecycle implementation and automated checks: explicit paid-work consent, start, graceful pause, status, redacted
  logs, single-instance protection, configuration and optional login startup.
- [x] Development distribution: Windows/macOS/Linux native build matrix, checksums,
  installed-runtime smoke tests and gated dashboard downloads.
- [x] Guide and automated verification: first-install walkthrough and recovery coverage;
  distinguish tested operating systems from merely buildable packages.
- [ ] Public release: signing/notarization, publication approval, signed install/update
  validation and real login/reboot startup validation on supported operating systems.

Implementation: three-step native Swing shell, OS credential adapters, explicit worker start,
batch-draining pause, bounded redacted logs, per-user single-instance lock, and
opt-in user login startup are implemented. Windows MSI, macOS ARM64 DMG and Linux
x64 DEB builds and native key-store round trips passed. Native setup screenshots
were inspected. The real child-worker test proves heartbeat, polling and graceful
idle pause without provider calls. The final matrix also exercises native package
installation/launch on disposable hosts. The public download/update page remains
explicitly unpublished until signed release artifacts exist. Prices for custom
models remain explicit overrides, not invented defaults.

Remaining owner-dependent release gate: Apple Developer signing/notarization and
Windows code signing, permission to publish a release, and testing the signed
install/update path through normal OS trust prompts. Do not bypass those prompts
or claim unsigned CI artifacts are trusted public installers.

## Security and verification

## Onboarding reliability and polish follow-up (1.0.1)

- [x] Modern HiDPI-aware dark desktop theme and existing favicon only.
- [x] Explicit restored connection, hidden saved-key explanation, remembered new
  runner names, and safe fallback labels for older installations.
- [x] Reopen/cancel pairing controls, bounded polling regression tests, and recovery
  of old dashboard URLs carrying valid pairing challenges.
- [x] Free heartbeat and local key-store checks; allowlisted diagnostics export.
- [x] No-provider-call start/pause/restart and restored-window regression tests.
- [x] Windows installer upgrade and uninstall/reinstall state-preservation checks
  on disposable CI hosts; real OS reboot/login acceptance remains deferred.
- [ ] Paid real-provider delivery and signed public release remain separate gates.

## Security boundaries

Provider keys never reach the control plane. Browser approval never starts paid
work. Pairing proofs are high-entropy and only their SHA-256 challenges are stored.
An administrator explicitly checks the desktop/browser fingerprint before approval.
An expired or consumed approval cannot enroll another runner. Native key stores
must fail closed rather than silently save plaintext. Do not auto-install Docker
or grant host privileges. Test using fake providers and disposable identities.

Code-signing/notarization require owner credentials; unsigned development artifacts
must never be presented as trusted production installers. Existing live Docker
runner and provider account remain untouched throughout development.
