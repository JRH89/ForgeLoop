param([string]$BaseUrl='http://localhost:5173')
$ErrorActionPreference='Stop'
# Run only against CI's disposable development stack. It creates one test identity, never claims work.
if (-not ([Uri]$BaseUrl).IsLoopback) { throw 'Package smoke test requires a local disposable development stack.' }
$root=Join-Path ([IO.Path]::GetTempPath()) ('forgeloop-package-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
$previous=$env:FORGELOOP_RUNNER_STATE_FILE
try {
    $zip=Join-Path $root 'package.zip'
    Invoke-WebRequest "$BaseUrl/downloads/forgeloop-runner.zip" -OutFile $zip
    $checksumFile=Join-Path $root 'package.sha256'
    Invoke-WebRequest "$BaseUrl/downloads/forgeloop-runner.zip.sha256" -OutFile $checksumFile
    $checksum=(Get-Content -LiteralPath $checksumFile -Raw).Split(' ')[0].Trim()
    if((Get-FileHash $zip -Algorithm SHA256).Hash -ne $checksum) { throw 'Runner package checksum mismatch' }
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $root 'runner')
    $response=Invoke-RestMethod "$BaseUrl/graphql" -Method Post -ContentType 'application/json' -Body '{"query":"mutation { issueRunnerRegistrationToken(organizationId: \"local-development\") }"}'
    $token=$response.data.issueRunnerRegistrationToken
    if(-not $token) { throw 'No enrollment token returned' }
    $env:FORGELOOP_RUNNER_STATE_FILE=Join-Path $root 'identity'
    $jar=Join-Path $root 'runner/runner.jar'
    $token | & java -cp $jar io.forgeloop.runner.RunnerMain register-stdin $BaseUrl 'package-ci' 'git,provider,docker'
    if($LASTEXITCODE -ne 0) { throw 'Packaged runner enrollment failed' }
    & java -cp $jar io.forgeloop.runner.RunnerMain heartbeat $BaseUrl $env:FORGELOOP_RUNNER_STATE_FILE
    if($LASTEXITCODE -ne 0) { throw 'Packaged runner heartbeat failed' }
    Write-Host 'Downloaded runner checksum, enrollment, and heartbeat passed without provider calls.'
} finally {
    $env:FORGELOOP_RUNNER_STATE_FILE=$previous
    $resolved=[IO.Path]::GetFullPath($root)
    if($resolved.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()),[StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -like 'forgeloop-package-test-*') { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
