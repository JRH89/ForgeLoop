$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Wait-PostgresFinalServer.ps1')

# Exercise the temporary-init-server race without starting or modifying Docker.
$script:pidChecks = 0
$script:queryChecks = 0
function docker {
    if ($args[0] -ne 'exec' -or $args[1] -ne 'test-restore') { throw 'Unexpected Docker invocation' }
    $global:LASTEXITCODE = 0
    switch ($args[2]) {
        'cat' {
            $script:pidChecks++
            if ($script:pidChecks -le 2) { 'docker-entrypoi' } else { 'postgres' }
        }
        'pg_isready' { }
        'psql' { $script:queryChecks++; '1' }
        default { throw 'Unexpected Docker subcommand' }
    }
}
function Start-Sleep { param([int]$Seconds) }

Wait-PostgresFinalServer -Container 'test-restore' -Database 'forgeloop' -Username 'forgeloop' -Attempts 4 -DelaySeconds 0
if ($script:pidChecks -ne 3 -or $script:queryChecks -ne 1) {
    throw 'The waiter accepted the temporary initialization server or skipped the SQL probe.'
}

$script:pidChecks = 0
function docker {
    if ($args[2] -ne 'cat') { throw 'A temporary server must not be queried or restored.' }
    $global:LASTEXITCODE = 0
    $script:pidChecks++
    'docker-entrypoi'
}
try {
    Wait-PostgresFinalServer -Container 'test-restore' -Database 'forgeloop' -Username 'forgeloop' -Attempts 2 -DelaySeconds 0
    throw 'The waiter did not time out.'
} catch {
    if ($_.Exception.Message -notlike 'Final PostgreSQL server*did not become ready*') { throw }
}
if ($script:pidChecks -ne 2) { throw 'The waiter did not enforce its retry limit.' }
Write-Host 'PostgreSQL final-server wait passed: initialization race and bounded timeout.'
