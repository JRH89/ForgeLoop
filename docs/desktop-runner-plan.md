# Desktop runner onboarding

Replace the primary terminal-based journey with download, install, browser
approval, local provider setup, and an explicit start. Keep the CLI as an advanced
fallback. Existing runners must not be re-enrolled or migrated implicitly.

## Acceptance slices

- [x] Browser pairing implementation and unit tests: administrator approval bound to a desktop-generated secret,
  short expiry, single-use exchange, organization isolation, no credentials in URLs.
  Disposable-stack integration runs in CI; release acceptance remains gated on it.
- [ ] Desktop setup: bundled Java, prerequisite checks, secure native credential
  storage, provider/model selection and editable dated price estimates.
- [ ] Lifecycle: explicit paid-work consent, start, graceful pause, status, redacted
  logs, single-instance protection, configuration and optional login startup.
- [ ] Distribution: Windows/macOS/Linux native build matrix, checksums, installer
  smoke tests, signed release/update trust policy and dashboard downloads.
- [ ] Guide and verification: first-install walkthrough and recovery coverage;
  distinguish tested operating systems from merely buildable packages.

Implementation: native Swing shell, OS credential adapters, explicit worker start,
batch-draining pause, bounded redacted logs, per-user single-instance lock, and
opt-in user login startup are implemented. Windows MSI and Linux DEB builds passed
the first matrix run; macOS packaging required a positive installer version, now
fixed. Native secret-store round trips and the final matrix remain release gates.
Do not change the public download links until release artifacts are trusted.

## Security and verification

Provider keys never reach the control plane. Browser approval never starts paid
work. Pairing proofs are high-entropy and only their SHA-256 challenges are stored.
An administrator explicitly checks the desktop/browser fingerprint before approval.
An expired or consumed approval cannot enroll another runner. Native key stores
must fail closed rather than silently save plaintext. Do not auto-install Docker
or grant host privileges. Test using fake providers and disposable identities.

Code-signing/notarization require owner credentials; unsigned development artifacts
must never be presented as trusted production installers. Existing live Docker
runner and provider account remain untouched throughout development.
