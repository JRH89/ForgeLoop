# Desktop runner (development preview)

The desktop application is being verified before replacing the existing ZIP/CLI
installation. Do not treat an unsigned CI artifact as a production release.
Your existing Docker or CLI runner does not need to be replaced to use ForgeLoop.

## First connection

The desktop uses four sidebar pages: **Runner** for start/pause and setup status,
**Connection** for pairing and local prerequisites, **Provider** for your model,
key and pricing, and **Activity** for session logs, updates and diagnostics.
At narrow window sizes the sidebar becomes an icon rail; tooltips and accessible
names retain each destination. Opening the application does not start work.

If your server was replaced or its database was reset, open **Connection** and
select **Check saved connection**. A saved identity is not proof that the server
still recognizes it. Use **Reconnect to ForgeLoop** while the worker is stopped
to pair again. This backs up the old identity in the private runner directory;
it preserves provider keys, settings and workspaces. Save provider settings after
pairing with a different server address before starting work.

1. Install the native package built for your OS and architecture. Java is bundled.
2. Open ForgeLoop Runner and select **Check requirements**. The app checks Git
   and the Docker engine separately, then shows the official install guide for
   your operating system. Docker must report Linux containers. On Windows,
   switch Docker Desktop out of Windows-container mode if prompted. Linux desktop
   key storage additionally needs `secret-tool` (libsecret tools) and an unlocked
   Secret Service keyring. Headless users retain the CLI.
3. Install missing tools, start Docker for first-time pairing, and check requirements again. Git must
   answer its version check; Docker must connect to a ready Linux-container engine.
   Neither check contacts ForgeLoop or a model provider.
4. Enter your ForgeLoop address and a recognizable name. Select
   **Connect in browser**. Pairing checks Git and Docker again before creating an
   approval request. Compare the displayed fingerprint in both apps.
5. Sign in with GitHub if necessary, then explicitly approve as an organization
   administrator. The sign-in link opens a separate tab so the pairing request
   remains available. Return to the original tab to approve. Approval expires
   after five minutes; the desktop waits up to ten minutes for approval.
6. On the Provider page select the provider, model, and API key. The app looks
   up public base text-token rates automatically, without using your API key.
   Review the displayed source and lookup date. Use **manual prices** for
   account-specific terms; an unknown or offline model can be saved with N/A
   cost estimates. Save to advance to the Runner page.
7. On Connection, check requirements once more if anything changed. Open Runner and click
   **Start runner** and confirm potential API charges. If the installed local
   Docker engine is stopped, the app starts it and waits for Linux containers
   before any work is claimed. Starting Docker itself makes no model request.

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

## Starting Docker automatically

**Start runner** checks Git and Docker, then starts an installed local engine
when it is stopped. It waits up to two minutes for Docker to report Linux
containers. Progress appears in the log while the window remains responsive.
Use **Cancel Docker startup** to stop waiting and leave the runner stopped;
Docker may remain running if it has already launched. No model fees are incurred
until the runner starts processing eligible work.

- **Windows:** uses `docker desktop start --detach` when available, with a
  fallback to the installed Docker Desktop application. Both all-user and
  per-user installations are supported. Complete any Docker Desktop first-run
  or operating-system prompts before retrying.
- **macOS:** uses the Docker Desktop command when available, with a fallback
  to opening the installed Docker application. Docker Desktop still needs its
  normal first-run setup and permissions.
- **Linux:** starts the service for the selected local engine: Docker Desktop
  through its user service, rootless Docker through the user's `docker.service`,
  or Docker Engine through the system `docker.service`. A system service may
  show the desktop's native administrator authentication prompt. If no
  authentication agent is available or permission is denied, start the service
  manually and retry. Rootless startup needs a working user service session.

**Check requirements** and browser pairing only inspect readiness; they never
start Docker. Missing Git or Docker, Windows-container mode, stopped remote or
custom Docker endpoints, and socket-access failures show guidance instead of starting
an unrelated engine. The app does not install Docker, enable startup on boot,
change container mode, or change your selected Docker context. Docker Desktop
may change the CLI's default context when it launches; ForgeLoop explicitly pins
the original connection for its readiness checks and runner process, including
when the engine was already running. It tries to
restore Docker Desktop's change to the CLI's default context, but this is
best-effort: after cancellation, a still-launching Docker Desktop may change that
default later. Check your CLI context before unrelated Docker work.

These paths follow the official [Docker Desktop start command](https://docs.docker.com/reference/cli/docker/desktop/start/),
[Linux Desktop startup](https://docs.docker.com/desktop/setup/install/linux/ubuntu/#launch-docker-desktop),
[rootless service lifecycle](https://docs.docker.com/engine/security/rootless/tips/),
and [Docker Engine startup](https://docs.docker.com/engine/daemon/start/).
Automated tests exercise platform selection, readiness, timeout, cancellation,
and failure handling without starting a real daemon. No native Docker application
or service was started during local verification. Actual Docker Desktop and Linux
service startup still require acceptance on each target operating system; mocked
tests do not establish live daemon compatibility.

## Smaller windows and display scaling

Connect, Provider, and Run each scroll vertically, so settings and controls remain
reachable in a smaller window or with operating-system display scaling. Fields
have labels above their inputs; long status messages wrap, and action buttons
move onto another row when needed. The initial window targets 940 × 800 logical
pixels and can be reduced to 640 × 560; both sizes are capped to the available
screen area on small or scaled displays. Scroll within the selected tab to reach
lower sections; the activity log also has its own scroll area.

Local layout checks use a separate test window and saved test settings. They do
not start paid work, replace your installed runner, or publish a release. Automated
checks cover all four pages at narrow and wide widths, 14/21/28-point UI fonts,
long status updates, action wrapping, and vertical reachability. Rendered Windows
test windows are inspected locally; native macOS/Linux scaling still needs
acceptance on those target desktops.

## Controls and local data

**Pause runner** allows the active batch to complete before stopping
new work. It is not an emergency cancellation and active calls can still cost
money. Optional **Start work at sign-in** is explicit consent to automatic paid
work on future sign-ins; it follows the same Docker startup and readiness checks,
and your keyring must be unlocked.
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
is a self-contained app image; `-PackageType msi`, `dmg`, `deb`, `rpm`, `tar.gz`,
or `pkg.tar.zst` builds a native or portable development package. The CI matrix
builds Windows x64/ARM64, macOS Intel/Apple Silicon, and Linux x64/arm64. Linux
packages are smoke-tested, including a pacman install on Arch. OS/architecture
support follows that matrix, not an assertion that every distribution or CPU has
been tested.

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
plane. Native package CI covers all supported operating systems and
architectures; Windows also tests 1.0.0-to-1.0.1 installer replacement and private-state survival across
uninstall/reinstall. These checks do not replace real-provider delivery evidence
or actual OS sign-in/reboot acceptance. Public previews use the GitHub release feed.
