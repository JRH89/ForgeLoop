param(
    [string]$ComposeProject = "forgeloop",
    [string]$Database = "forgeloop",
    [string]$Username = "forgeloop",
    [string]$BackupFile
)

$ErrorActionPreference = "Stop"
$sourceContainer = "$ComposeProject-postgres-1"
$drillContainer = "$ComposeProject-restore-drill-$([guid]::NewGuid().ToString('N'))"
$backupPath = Join-Path ([System.IO.Path]::GetTempPath()) "forgeloop-restore-$([guid]::NewGuid().ToString('N')).dump"
$containerBackup = "/tmp/forgeloop-restore.dump"

try {
    docker inspect $sourceContainer *> $null
    if ($LASTEXITCODE -ne 0) { throw "Source PostgreSQL container '$sourceContainer' is not running." }

    if ($BackupFile) {
        Copy-Item -LiteralPath (Resolve-Path -LiteralPath $BackupFile).Path -Destination $backupPath
    } else {
    docker exec $sourceContainer pg_dump --format=custom --no-owner --no-acl --file=$containerBackup --username=$Username $Database
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed." }
    docker cp "${sourceContainer}:${containerBackup}" $backupPath
    if ($LASTEXITCODE -ne 0) { throw "Could not copy the backup from the source container." }
    docker exec $sourceContainer rm -f $containerBackup
    }

    # PostgreSQL 18 stores its versioned data directory beneath /var/lib/postgresql.
    docker run --detach --network none --name $drillContainer --tmpfs /var/lib/postgresql:rw,uid=70,gid=70,mode=0700 `
        --env POSTGRES_DB=$Database --env POSTGRES_USER=$Username --env POSTGRES_PASSWORD=restore-drill `
        postgres:18-alpine *> $null
    if ($LASTEXITCODE -ne 0) { throw "Could not start restore target." }

    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $drillContainer pg_isready --username=$Username --dbname=$Database *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw "Restore target did not become ready." }

    docker cp $backupPath "${drillContainer}:${containerBackup}"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy the backup into the restore target." }
    docker exec $drillContainer pg_restore --exit-on-error --no-owner --no-acl --username=$Username --dbname=$Database $containerBackup
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed." }

    $result = docker exec $drillContainer psql --username=$Username --dbname=$Database --tuples-only --no-align `
        --command "select version || ':' || (select count(*) from repository_connection) from flyway_schema_history where success order by installed_rank desc limit 1;"
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($result)) { throw "Restored schema validation failed." }
    Write-Host "Restore drill passed. schema-and-repository-count=$($result.Trim())"
    # Validate operational records as well as schema presence, without printing secrets.
    docker exec $drillContainer psql --username=$Username --dbname=$Database --command "select (select count(*) from feature_run) as runs, (select count(*) from repository_connection) as repositories, (select count(*) from runner) as runners, (select count(*) from organization_membership) as memberships;"
    if ($LASTEXITCODE -ne 0) { throw "Restored operational record validation failed." }
}
finally {
    docker exec $sourceContainer rm -f $containerBackup *> $null
    docker rm --force $drillContainer *> $null
    if (Test-Path -LiteralPath $backupPath) { Remove-Item -LiteralPath $backupPath -Force }
}
