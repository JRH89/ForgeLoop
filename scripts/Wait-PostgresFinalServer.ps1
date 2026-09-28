function Wait-PostgresFinalServer {
    param(
        [Parameter(Mandatory)][string]$Container,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [ValidateRange(1, 300)][int]$Attempts = 30,
        [ValidateRange(0, 60)][int]$DelaySeconds = 1
    )

    # The official image starts a temporary PostgreSQL server while initializing
    # the cluster. It can pass pg_isready immediately before being shut down.
    # Only the final server has replaced the container's PID 1 entrypoint.
    foreach ($attempt in 1..$Attempts) {
        $pidOne = docker exec $Container cat /proc/1/comm 2>$null
        if ($LASTEXITCODE -eq 0 -and $pidOne.Trim() -eq 'postgres') {
            docker exec $Container pg_isready --quiet --username=$Username --dbname=$Database *> $null
            if ($LASTEXITCODE -eq 0) {
                $query = docker exec $Container psql --username=$Username --dbname=$Database --tuples-only --no-align --command 'select 1' 2>$null
                if ($LASTEXITCODE -eq 0 -and $query.Trim() -eq '1') { return }
            }
        }
        if ($attempt -lt $Attempts) { Start-Sleep -Seconds $DelaySeconds }
    }
    throw "Final PostgreSQL server in '$Container' did not become ready after $Attempts attempts."
}
