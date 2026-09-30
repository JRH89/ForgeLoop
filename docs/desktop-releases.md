# Desktop release publishing

Run **Publish desktop preview** in GitHub Actions from `master`, supplying a new
numeric installer version such as `1.0.6`. It invokes the shared desktop workflow
at that commit, builds and tests Windows x64 MSI, macOS arm64 and x64 DMG, plus
Linux x64 DEB, RPM, and AppImage packages. Linux formats target Debian/Ubuntu/Mint,
Fedora and RPM-based systems, and AppImage-compatible distributions such as Arch.
The workflow also tests Windows upgrade-state preservation.

After every platform passes, the publish job creates the tag
`desktop-v<VERSION>-preview.1`, uploads all six installers, individual `.sha256`
files, and `SHA256SUMS` to a draft release, then publishes it as a prerelease. It uses
the workflow token; no new signing or model credentials are needed. The version
must be unique. Published assets are never overwritten. If publication fails
after draft creation, inspect the draft before retrying with a new version.

Enable repository release immutability before publishing. Assets and tags become
locked on publication; GitHub also provides a release attestation. Signing and
Apple notarization remain separate work. This workflow only publishes unsigned
previews and never marks them as the latest stable GitHub release.

## Website discovery

`/app/runner-downloads` is public. The footer and desktop app link there.
`/downloads/desktop-releases.json` proxies the repository's public GitHub release
API with a shared five-minute Nginx cache, verified TLS, no forwarded credentials,
and stale-cache recovery for upstream outages or rate limits.

The browser selects the highest complete desktop version among the latest 20
GitHub releases, matching our tag and asset naming convention. New complete
releases have Windows x64 MSI, macOS arm64/x64 DMGs, and Linux x64 DEB, RPM, and
AppImage assets. The previous four-asset set (Windows, both macOS builds, and
Linux DEB) remains supported during migration. Other releases, drafts, incomplete
target sets, wrong URLs, and missing SHA-256 digests are ignored.
URLs and hashes come from the same release response, so a new version cannot
silently use a previous version's checksum. Installers download directly from
GitHub, not the control plane. An explicitly published local manifest is the
fallback; otherwise users get the GitHub Releases link and CLI package.

No website rebuild is needed for subsequent releases. Refreshing the downloads
page discovers releases after the shared cache expires. Updates are manual:
pause, finish work, close, install, reopen. There is no automatic installer launch.
The desktop app's **Check for updates** uses this same feed, compares its
embedded package version, and displays its platform asset URL and checksum. It
rejects incomplete releases, wrong-version assets, non-GitHub URLs, and missing
or malformed digests. If work is active, it withholds the handoff to the
downloads page. Users still install packages themselves after pausing and
closing the app.

Linux DEB packages are for Debian, Ubuntu, and Linux Mint; RPM packages are for
Fedora and compatible RPM distributions. The portable x64 AppImage is the option
for Arch and other AppImage-compatible desktops. AppImages can require FUSE 2;
the downloads page documents the extraction fallback. Linux key storage requires
`secret-tool` and an unlocked desktop Secret Service keyring, regardless of
package format.

## Verification

Native CI tests installed launchers, favicon resources, OS secret storage, and
Windows upgrade/uninstall state preservation. Release staging recomputes hashes
before upload. Website tests cover complete releases, drafts, malformed assets,
preview labeling, checksum details, and fallback behavior. A public smoke check
must confirm all release asset URLs and displayed hashes after first publication.
