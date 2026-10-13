# Deferred launch checks

Agreed on 2026-09-26; updated with October 9 validation evidence. Unchecked items
remain open; deferral is not a passing result.

- [ ] **Off-host backup and restore — deferred.** Select encrypted, access-controlled
  storage and retention, copy database/evidence bundles, and restore on another host.
  Current automatic backups protect local recovery only.
- [ ] **Reboot/server validation — planned soon.** Verify Docker startup, durable
  database/evidence/runner identity, tunnel routing, authenticated dashboard access,
  runner heartbeats, and a fresh backup after an actual reboot and server deployment.
- [x] **Second-repository end-to-end run — validated October 9.** The unattended
  issue-to-merge run completed without a human approval click. See
  [recorded evidence](second-repository-validation-2026-10-09.md). This does not
  close the operational readiness checks.
- [ ] **Remaining production evidence.** Populated evidence restore, external alert
  delivery, staging burn-in, and explicit go/no-go review still need validation.

See `implementation-slices.md` (Slices 8–9) and `reliability.md` for evidence already
collected. Runner onboarding documentation does not close these launch gates.
