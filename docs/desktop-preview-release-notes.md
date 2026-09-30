ForgeLoop Runner desktop preview for Windows x64, macOS Apple Silicon and Intel,
and Linux x64. Linux downloads include DEB for Debian/Ubuntu/Mint, RPM for Fedora,
and a portable AppImage for Arch and other compatible distributions. Java is
bundled; Git and Docker must be installed.

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
`sha256sum installer.rpm`, or `sha256sum installer.AppImage` as appropriate.
Compare the full value with the published checksum. A checksum verifies file
integrity; it does not replace publisher signing.

The AppImage may require FUSE 2. If FUSE is unavailable, try
`APPIMAGE_EXTRACT_AND_RUN=1 ./installer.AppImage`. Linux credential storage also
requires `secret-tool` and an unlocked desktop Secret Service keyring.

For updates, pause the runner, wait for current work to finish, close the app,
and install the new version. Runner identity, settings and provider keys are
stored outside the installation directory and are preserved during upgrades.

Downloads and setup: https://forgeloop.hookerhillstudios.com/app/runner-downloads
