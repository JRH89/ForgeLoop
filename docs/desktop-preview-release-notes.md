ForgeLoop Runner desktop preview for Windows x64, macOS Apple Silicon and Intel,
and Debian/Ubuntu Linux x64. Java is bundled; Git and Docker must be installed.

Install the package for your operating system, open ForgeLoop Runner, approve
the browser pairing request, and configure your provider key locally. Starting
the runner can process eligible work and incur provider charges.

These preview installers are unsigned, and the macOS apps are not notarized.
Windows and macOS may display publisher warnings or block opening the app.
Only proceed if your device policy permits unsigned previews. Signed releases
will be provided separately.

Each installer has a companion `.sha256` file; `SHA256SUMS` lists all installers.
On Windows use `Get-FileHash .\installer.msi -Algorithm SHA256`, on macOS use
`shasum -a 256 installer.dmg`, or on Linux use `sha256sum installer.deb`.
Compare the full value with the published checksum. A checksum verifies file
integrity; it does not replace publisher signing.

For updates, pause the runner, wait for current work to finish, close the app,
and install the new version. Runner identity, settings and provider keys are
stored outside the installation directory and are preserved during upgrades.

Downloads and setup: https://forgeloop.hookerhillstudios.com/app/runner-downloads
