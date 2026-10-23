ForgeLoop Runner desktop preview for Windows x64 and ARM64, macOS Apple Silicon
and Intel, and Linux x64 and arm64. Linux downloads include DEB for Debian/Ubuntu,
RPM for Fedora/RHEL/openSUSE, portable `.tar.gz` archives, and native Arch
`.pkg.tar.zst` packages. Java is bundled; Git and Docker must be installed.

## What changed in this preview

- **Guarded multi-turn execution:** Writing tasks can use the agent loop when both
  the repository policy and the runner-local `FORGELOOP_AGENT_LOOP_ENABLED=true`
  switch permit it. The switch is off by default; existing installations retain
  their current execution behavior after upgrading.
- **Lease and budget controls:** Loop execution renews its lease, reports usage
  for every provider turn, reserves known spend, and stops when the lease is lost
  or a policy or budget limit is reached. A final lease check prevents cancelled
  work from being returned for delivery. Official verification remains separate.
- **Execution evidence:** Loop policy is preserved with task context and execution
  pins. Local journals remain private; remote run-record upload still requires
  the repository's separate opt-in.
- **Security:** Updated the runner’s Jackson dependency to 2.21.7 to address
  the high-severity findings detected by the release security scan.
- **Maintenance:** Provider diagnostics and standalone verification commands are
  separated from runner orchestration while preserving the existing CLI commands.

The new loop requires the matching control-plane update. Installers alone do not
activate it. Pause and close the runner before updating; identity, settings, and
provider keys remain in their existing external state directories.

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
