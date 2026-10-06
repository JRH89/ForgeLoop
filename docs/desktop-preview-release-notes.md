ForgeLoop Runner desktop preview for Windows x64 and ARM64, macOS Apple Silicon
and Intel, and Linux x64 and arm64. Linux downloads include DEB for Debian/Ubuntu,
RPM for Fedora/RHEL/openSUSE, portable `.tar.gz` archives, and native Arch
`.pkg.tar.zst` packages. Java is bundled; Git and Docker must be installed.

## What changed in this preview

- **Docker startup:** Start runner can start a recognized, installed local Docker
  engine on Windows, macOS, or Linux, then wait for Linux-container readiness.
  Startup is bounded and cancellable; no model work starts before readiness.
  Permission failures and remote/custom Docker endpoints show actionable guidance
  instead of starting a different engine. Linux may use the system's authorization
  dialog; ForgeLoop never asks for an administrator password.
- **Desktop layout:** Connect, Provider, and Run now use labeled, stacked fields,
  compact wrapping buttons, and width-aware scrolling. Long status messages wrap
  instead of pushing controls off-screen. The initial window also fits the screen's
  usable area on scaled displays.
- **Upgrade safety:** The Windows upgrade keeps your existing connection, provider
  settings, and protected API-key files. Pause and close the runner before updating.

Automatic Docker startup does not install Docker, enable it at boot, change its
container mode, or replace your selected remote/custom endpoint. First-time pairing
still requires a running Docker engine. Checking requirements does not start Docker
or call a model.

## Setup and costs

The Provider step now looks up public base text-token prices for supported
models. Account-specific rates can still be entered manually. Unknown or
unavailable rates show N/A rather than a misleading zero-cost estimate.

Install the package for your operating system, open ForgeLoop Runner, approve
the browser pairing request, and configure your provider key locally. Starting
the runner can process eligible work and incur provider charges.

These preview installers are unsigned, and the macOS apps are not notarized.
Windows and macOS may display publisher warnings or block opening the app.
Only proceed if your device policy permits unsigned previews. Signed releases
will be provided separately.

Each installer has a companion `.sha256` file; `SHA256SUMS` lists all installers.
On Windows use `Get-FileHash .\installer.msi -Algorithm SHA256`, on macOS use
`shasum -a 256 installer.dmg`, or on Linux use `sha256sum installer.deb`,
`sha256sum installer.rpm`, `sha256sum installer.tar.gz`, or
`sha256sum installer.pkg.tar.zst` as appropriate.
Compare the full value with the published checksum. A checksum verifies file
integrity; it does not replace publisher signing.

On Arch Linux (including Omarchy), install the native package with
`sudo pacman -U ./installer.pkg.tar.zst`. Alternatively, extract the portable
archive and launch `./run-forgeloop-runner.sh`; this avoids AppImage and FUSE.
Linux credential storage requires `secret-tool` and an unlocked desktop Secret
Service keyring.

For updates, pause the runner, wait for current work to finish, close the app,
and install the new version. Runner identity, settings and provider keys are
stored outside the installation directory and are preserved during upgrades.

Downloads and setup: https://forgeloop.hookerhillstudios.com/app/runner-downloads
