param([string]$BaseUrl='http://localhost:5173')
$ErrorActionPreference='Stop'
# Only use a disposable development stack: this creates a test runner, never claims work.
if (-not ([Uri]$BaseUrl).IsLoopback) { throw 'Pairing smoke test requires a local disposable development stack.' }
function Query([string]$query, [hashtable]$variables) {
    Invoke-RestMethod "$BaseUrl/graphql" -Method Post -ContentType 'application/json' -Body (@{query=$query;variables=$variables}|ConvertTo-Json -Depth 5 -Compress)
}
$bytes=New-Object byte[] 32
$random=[Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($bytes); $random.Dispose()
$verifier=([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
$sha=[Security.Cryptography.SHA256]::Create()
$challenge=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier)))).Replace('-','').ToLowerInvariant()
$sha.Dispose()
$exchange='mutation($verifier:String!){exchangeRunnerPairing(verifier:$verifier){runner{id organizationId name} credential}}'
$pending=Query $exchange @{verifier=$verifier}
if($pending.errors -or $pending.data.exchangeRunnerPairing) { throw 'Expected pending pairing' }
$approval=Query 'mutation($challenge:String!,$name:String!){approveRunnerPairing(challenge:$challenge,name:$name)}' @{challenge=$challenge;name='desktop-pairing-ci'}
if(-not $approval.data.approveRunnerPairing) { throw 'Approval failed' }
$enrollment=(Query $exchange @{verifier=$verifier}).data.exchangeRunnerPairing
if(-not $enrollment.credential -or $enrollment.runner.organizationId -ne 'local-development' -or $enrollment.runner.name -ne 'desktop-pairing-ci') { throw 'Enrollment not bound to approved organization/name' }
$heartbeat=Query 'mutation($runnerId:ID!,$credential:String!){runnerHeartbeat(runnerId:$runnerId,credential:$credential){id}}' @{runnerId=$enrollment.runner.id;credential=$enrollment.credential}
if($heartbeat.errors) { throw 'Heartbeat failed' }
$replay=Query $exchange @{verifier=$verifier}
if(-not $replay.errors) { throw 'Replayed proof was accepted' }
Write-Output 'Browser approval, organization-bound exchange, heartbeat, and replay rejection passed. No provider calls.'
