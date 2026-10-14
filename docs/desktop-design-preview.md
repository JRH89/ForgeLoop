# Desktop design review

Local-only review of the runner's navy-and-teal desktop shell: compact sidebar,
page toolbar, bounded forms, consistent sections, thin scrollbars, and a
strongly rounded launcher silhouette. Sidebar branding supplies 1x–4x rasters
for sharp Windows scaling; the approved source mark is unchanged.

On Windows, run `scripts/Start-DesktopPreview.ps1` with `-JavaHome` pointing to
JDK 21 and `-MavenCommand` pointing to `mvn.cmd` if it is not on PATH.
This compiles classes only, not a release or installer. The preview opens on
Runner and allows navigation through Connection, Provider, and Activity.
It uses temporary sample settings, does not read the installed runner's keys
or identity, disables operational actions, and does not start paid work.
Close the preview normally when finished.
Minimize, maximize/restore, and close remain available; only actions inside the
app's content area are disabled.

Verification: desktop layout/window tests cover all pages at narrow/wide sizes
and large fonts. Icon tests check transparent rounded corners, deterministic
native output, and device-resolution variants. Installer/release builds and
publishing remain pending explicit design approval.
