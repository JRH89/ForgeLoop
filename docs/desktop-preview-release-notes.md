ForgeLoop Runner desktop preview for Windows x64 and ARM64, macOS Apple Silicon
and Intel, and Linux x64 and arm64. Linux downloads include DEB for Debian/Ubuntu,
RPM for Fedora/RHEL/openSUSE, portable `.tar.gz` archives, and native Arch
`.pkg.tar.zst` packages. Java is bundled; Git and Docker must be installed.

## What changed in this preview

- **Worktree sandbox hardening:** Agent file reads and writes now refuse Windows
  aliases of the `.git` directory (trailing dots or spaces, 8.3 short names such
  as `GIT~1`, and NTFS stream suffixes), closing a path that could reach Git
  metadata on Windows hosts. Ordinary names such as `.github/` are unaffected.
- **Git hook isolation:** Every Git command the runner executes in a repository
  worktree disables repository hooks and filesystem monitors, so a checked-out
  repository cannot run its own code through Git during checkout or commit.
- **Image updates:** The runner container image applies current Alpine security
  updates, clearing a high-severity compression-library finding from the release
  scan.

These changes are runner-side and work with the current control plane. Pause and
close the runner before updating; identity, settings, and provider keys remain in
their existing external state directories.

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

Downloads and setup: https://forgeloop.codefrontlabs.com/app/runner-downloads
