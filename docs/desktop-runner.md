# Desktop runner (development preview)

The desktop application is being verified before replacing the existing ZIP/CLI
installation. Do not treat an unsigned CI artifact as a production release.
Your existing Docker or CLI runner does not need to be replaced to use ForgeLoop.

## First connection

1. Install the native package built for your OS and architecture. Java is bundled.
2. Install Git and Docker separately if necessary; start Docker in Linux-container
   mode. Linux desktop key storage additionally needs `secret-tool` (libsecret
   tools) and an unlocked Secret Service keyring. Headless users retain the CLI.
3. Open ForgeLoop Runner. Enter your ForgeLoop address and a recognizable name.
   Select **Connect in browser**. Compare the displayed fingerprint in both apps.
4. Sign in with GitHub if necessary, then explicitly approve as an organization
   administrator. The sign-in link opens a separate tab so the pairing request
   remains available. Return to the original tab to approve. Approval expires
   after five minutes; the desktop waits up to ten minutes for approval.
5. On the Provider step select the provider, model, and API key. Sonnet 5 price
   defaults are dated estimates. Pricing overrides are collapsed by default;
   custom models require explicit prices. Save to advance to the Run step.
6. Check Git and Docker. Click **Start runner** and confirm potential API charges.

No enrollment token needs copying. The local pairing secret never enters a URL.
Connecting, saving settings, and checking prerequisites do not call a model.

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

Pause before editing settings or upgrading. Reopen after replacing the app;
the existing identity and settings remain. **Downloads / updates** opens the
deployment's release page; it does not silently replace or execute a binary.
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
calls use [JNA](https://github.com/java-native-access/jna). Sonnet defaults reference
[Anthropic pricing](https://platform.claude.com/docs/en/about-claude/pricing).

Still required before public release: signed
Windows packages, Apple Developer signing/notarization, trusted checksums and
release hosting and publication of `frontend/public/downloads/desktop-manifest.json`,
OS login validation, and signed-install/update acceptance. The manifest remains
unpublished until installers have trusted signatures and recorded SHA-256 hashes.
Do not bypass OS security warnings to market this as production-ready.
