# Workstation reliability and recovery

Compose gives PostgreSQL a stable named volume and restarts PostgreSQL, the
control plane, both web proxies, backups, and the tunnel after Docker restarts.
The installed runner also uses `unless-stopped` and retains its identity in its
host-mounted state directory. Docker Desktop must itself start after login.
Explicitly stopped services stay stopped; use `docker compose --profile tunnel
up -d --wait` to start the stack. Browser sessions require login again after a
control-plane restart.

## Automatic backups

`database-backup` creates a custom-format PostgreSQL dump every six hours and
at service startup in the `postgres-backups` Compose volume. Each snapshot has
a SHA-256 checksum, an archive of local evidence objects, and a `.complete`
marker written only after both complete. Health fails when no completed backup
exists within seven hours. Check `docker compose logs database-backup` and
`docker compose ps`. No backups are automatically deleted.

These are local recovery copies, not protection against loss of this computer.
Copy completed bundles to access-controlled, encrypted off-host storage before
production launch. Monitor disk usage and configure off-host retention before
introducing local pruning. Backups contain sensitive tenant data.

## Restore drill and recovery

Use `scripts/Test-PostgresRestore.ps1 -BackupFile <absolute-dump-path>` to restore
a saved dump into a unique, network-isolated temporary PostgreSQL container.
The script reports schema and operational record counts and removes only its
temporary target. Without `-BackupFile`, it dumps the live source first. CI
also runs the restore drill against its disposable database.

For actual recovery, stop writers, retain the current database volume, and
restore into a new named volume. Validate records and checksums before selecting
that volume with `FORGELOOP_POSTGRES_VOLUME` in `.env`. Restore the matching
artifact archive into a separate evidence volume and verify its checksum first.
Never use `down --volumes` on valuable deployment data.

Runner identity/configuration, GitHub secrets, and tunnel credentials are outside
the database backup. Retain these separately in encrypted operator-controlled
storage. Preserve the runner's `/state` mount across reinstalls; loss of that
identity requires administrator-controlled enrollment, not guessed credentials.

## Verified on 2026-09-26

- Recovered 10 runs and two repository connections from retained storage.
- Recreated PostgreSQL and confirmed those records persisted.
- Restored a scheduled database snapshot: schema 25, 10 runs, two connections,
  three runner records, and one membership.
- Restarted the app services and runner; the runner resumed authenticated
  heartbeats. Both Nginx configurations validate and resolve Docker service
  addresses dynamically.

A physical workstation reboot, off-host backup recovery, populated evidence
restore, external alert delivery, and server burn-in remain launch validation
gates. This drill does not claim those results.
