# Desktop runner redesign

Implemented locally on 2026-10-08. The second-repository production validation
is paused while the desktop experience is reviewed; this change does not provide
production-run evidence.

## Interface

- A sidebar replaces the numbered setup tabs. Runner, Connection, Provider and
  Activity each have a dedicated page, with the existing ForgeLoop favicon and
  a restrained dark palette with mint actions.
- Runner shows execution status, start/pause controls, saved connection and model,
  and direct links to setup. No fabricated work, token or cost metrics appear.
- Connection presents pairing and local prerequisites separately. Temporary
  fingerprint and browser-approval controls appear only during pairing.
- Provider retains protected key storage, automatic pricing and optional manual
  rates. Activity contains session logs, clear-view, diagnostics and update tools.
- Narrow windows collapse navigation to icons with tooltips and accessible names.
  Forms wrap to the available width; all content remains vertically scrollable.
- Status remains visible below every page. Setup failures appear there without
  unexpectedly changing the selected page. Cancellation appears during Docker
  preparation only.

## Connection recovery

A restored identity is labelled as saved, not verified. Check saved connection
sends a heartbeat only. Reconnect requires confirmation and an idle worker; it
moves only the identity to an owner-protected local backup. Provider keys,
configuration and task workspaces remain intact. Pairing writes a new identity,
and a changed endpoint requires saving matching provider settings before work
can start. Reconnection does not itself start work.

## Validation

The desktop tests render all four actual pages at 640px and 940px widths with
14pt, 21pt and 28pt form fonts. They check viewport boundaries, reachable buttons,
wrapped status text, restore behavior and no implicit credential access or paid
work. A navigation test exercises every sidebar destination and the collapsed
rail's accessible names. Recovery tests check identity backup, new enrollment,
and preservation of the key, configuration and workspaces.

Run `mvn verify` in `runner` for the full runner suite and packaged JAR. Actual
window captures are written to `runner/target/desktop-*.png`. The local Windows
app-image is a review build, not a published release.

Local results: the full runner verification passed with 373 tests, zero failures
or errors, and five optional/environment-dependent skips. After the final
error-display change, all 92 desktop tests passed with no skips. The local
Windows app-image includes a bundled Java runtime and the ForgeLoop favicon.
