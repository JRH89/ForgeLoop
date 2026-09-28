# Desktop runner (development preview)

The desktop application is being verified before replacing the existing ZIP/CLI
installation. Do not treat an unsigned CI artifact as a production release.
Your existing Docker or CLI runner does not need to be replaced to use ForgeLoop.

## First connection

1. Install the native package built for your OS and architecture. Java is bundled.
2. Open ForgeLoop Runner and select **Check requirements**. The app checks Git
   and the Docker engine separately, then shows the official install guide for
   your operating system. Docker must report Linux containers. On Windows,
   switch Docker Desktop out of Windows-container mode if prompted. Linux desktop
   key storage additionally needs `secret-tool` (libsecret tools) and an unlocked
   Secret Service keyring. Headless users retain the CLI.
3. Install missing tools, start Docker, and check requirements again. Git must
   answer its version check; Docker must connect to a ready Linux-container engine.
   Neither check contacts ForgeLoop or a model provider.
4. Enter your ForgeLoop address and a recognizable name. Select
   **Connect in browser**. Pairing checks Git and Docker again before creating an
   approval request. Compare the displayed fingerprint in both apps.
5. Sign in with GitHub if necessary, then explicitly approve as an organization
   administrator. The sign-in link opens a separate tab so the pairing request
   remains available. Return to the original tab to approve. Approval expires
   after five minutes; the desktop waits up to ten minutes for approval.
6. On the Provider step select the provider, model, and API key. The app looks
   up public base text-token rates automatically, without using your API key.
   Review the displayed source and lookup date. Use **manual prices** for
   account-specific terms; an unknown or offline model can be saved with N/A
   cost estimates. Save to advance to the Run step.
7. On the Run step, check requirements once more if anything changed. Click
   **Start runner** and confirm potential API charges. Start repeats the checks
   immediately before any work is claimed.

No enrollment token needs copying. The local pairing secret never enters a URL.
Connecting, saving settings, and checking prerequisites do not call a model.
Use the platform links in the Connect step for the official [Git downloads](https://git-scm.com/downloads)
and [Docker Desktop installation instructions](https://docs.docker.com/desktop/setup/install/).
Direct links: [Git for Windows](https://git-scm.com/download/win),
[Git for macOS](https://git-scm.com/download/mac), [Git for Linux](https://git-scm.com/download/linux),
[Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/),
[Docker Desktop for macOS](https://docs.docker.com/desktop/setup/install/mac-install/),
and [Docker Desktop for Linux](https://docs.docker.com/desktop/setup/install/linux/).

The pairing page itself loads without login so the fingerprint remains in the
original tab while GitHub sign-in opens separately. Approval still requires an
authenticated administrator. Verify this production proxy boundary with
`scripts/Test-AnonymousPairing.ps1 -BaseUrl https://your-forgeloop-host`.

## Controls and local data

**Pause after current work** allows the active batch to complete before stopping
new work. It is not an emergency cancellation and active calls can still cost
money. Optional **Start work at sign-in** is explicit consent to automatic paid
work on future sign-ins; Docker must also start, and your keyring must be unlocked.
Uncheck it and save to remove the app-managed login hook. Setup does not start work
immediately. The app refuses to close while work or setup is active. Start resumes the
same identity. The log pane is bounded and does not persist raw logs to disk.

State lives in the private `~/.forgeloop/desktop-runner` directory, outside the
installed application. An exclusive lock prevents a second desktop instance.
Provider keys use Windows DPAPI, macOS Keychain, or Linux Secret Service. There
is no plaintext fallback. Do not share the identity file or copy this state into
a second active installation. Administrators/root can still inspect processes.

Pause before editing settings or upgrading. **Check for updates** reads the
deployment's public release feed, compares the installed package version, and
shows the matching platform download, release notes, and SHA-256 digest. The
feed is the same five-minute cached GitHub Releases feed used by the website;
the app validates the published tag, complete platform set, GitHub asset URL,
and digest before displaying it. An incomplete, mismatched, or unavailable feed
produces an error instead of a guessed package. The app never downloads or runs
an installer. While work is active, it hides the handoff button; pause, wait for
the worker to stop, then open the downloads page. Close ForgeLoop Runner before
running the installer. Reopen after replacing the app; the existing identity
and settings remain. SHA-256 checks file integrity, not publisher identity.
Do not reconnect an existing identity
to a different ForgeLoop address. The current preview does not migrate CLI or
Docker identities. Uninstallation must retain state unless the user explicitly
chooses to remove it; native keychain entries need separate removal when retiring
the installation.

## Build and release boundaries

Build the tested runner JAR (`mvn verify` in `runner`), then run
`scripts/Build-DesktopRunner.ps1` with PowerShell 7 on the target OS. Its default
is a self-contained app image; `-PackageType msi`, `dmg`, or `deb` builds a native
development installer. The CI matrix builds on Windows, macOS and Linux and
smoke-tests each bundled runtime. OS/architecture support follows that matrix,
not an assertion that every Linux distribution or CPU has been tested.

Native packaging uses [jpackage](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-tool-user-guide.pdf)
and an explicit jlink runtime retaining the child-JVM launcher. Native credential
calls use [JNA](https://github.com/java-native-access/jna). The desktop and
advanced Windows installer look up base input/output rates from LiteLLM's public
[model price catalog](https://github.com/BerriAI/litellm/blob/main/model_prices_and_context_window.json)
and require an exact provider/model match and an official provider pricing source.
No API key or repository content is sent for this lookup. Rates are snapshots of
public base text-token prices, not invoices; caching, tools, context tiers,
regional billing and account discounts may differ. Automatic lookup refreshes
when selecting a model in the desktop app; saved prices remain a snapshot until
settings are reopened and saved again. Manual overrides remain available and
unknown/offline models are recorded as unpriced rather than free.

Unsigned previews are distributed through GitHub Releases with SHA-256 checksums
and automatic website discovery; see [release publishing](desktop-releases.md).
Still required for signed production distribution: Windows code signing, Apple
Developer signing/notarization, OS login validation, and signed-install/update
acceptance. Preview downloads identify their unsigned status explicitly.

## Desktop 1.0.1 usability and free checks

The desktop preview uses a consistent dark theme, the existing ForgeLoop favicon,
and a three-step layout. Reopening a configured installation selects Run and logs
that the existing connection/settings were restored. The API-key field stays blank
on purpose: **Saved key configured - leave blank to keep** is not a claim that the
provider accepted the key. Keys are decrypted only for an explicit local check,
settings save, or work start, not simply to render the window.

- **Check saved connection** sends a heartbeat only; it does not claim tasks.
- **Check saved key locally** checks OS-protected storage, not provider credit or
  API validity. Neither check calls a model.
- **Reopen approval page** opens the current fingerprint page while pairing is
  pending. **Cancel connection** stops polling after an in-flight request returns
  (up to the HTTP timeout), then Connect starts a fresh attempt. If approval wins
  the race, the issued identity is retained rather than discarded.
- New connections remember their display name. Older 1.0.0 installations that
  never saved a name show **Previously connected runner** without re-enrollment.
- **Export safe diagnostics** writes an allowlisted runtime/status summary. It
  excludes keys, runner credentials, account/repository identifiers and raw task
  logs. The in-app log remains session-only; it is intentionally not reloaded.

Offline regression coverage includes pairing pending/success/timeout/cancellation,
connection errors, saved-window restoration without decrypting keys, diagnostics
redaction, and child-worker start/pause/restart against a loopback fake control
plane. Native package CI covers all three supported build targets; Windows also
tests 1.0.0-to-1.0.1 installer replacement and private-state survival across
uninstall/reinstall. These checks do not replace real-provider delivery evidence
or actual OS sign-in/reboot acceptance. Public previews use the GitHub release feed.
