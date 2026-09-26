$ErrorActionPreference = 'Stop'
# Offline integration test: simulate Java/control-plane responses, never contact a provider.
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('forgeloop-installer-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
Copy-Item -Path (Join-Path $PSScriptRoot '../runner/install/*.ps1') -Destination $testRoot
New-Item -ItemType File -Path (Join-Path $testRoot 'runner.jar') | Out-Null
$global:forgeLoopInstallerTestAnswers = [Collections.Generic.Queue[string]]::new()
@('https://example.test','test-runner','enrollment-placeholder','anthropic','test-model','3','15','provider-placeholder') | ForEach-Object { $global:forgeLoopInstallerTestAnswers.Enqueue($_) }
function Read-Host { param([string]$Prompt,[switch]$AsSecureString) $answer=$global:forgeLoopInstallerTestAnswers.Dequeue(); if($AsSecureString){ConvertTo-SecureString $answer -AsPlainText -Force}else{$answer} }
function docker { $global:LASTEXITCODE=0; 'linux' }
function git { $global:LASTEXITCODE=0 }
function New-ScheduledTaskAction { param($Execute,$Argument,$WorkingDirectory) if($Argument -notmatch '-WindowStyle Hidden'){throw 'Startup must be hidden'}; @{Execute=$Execute;Argument=$Argument;WorkingDirectory=$WorkingDirectory} }
function New-ScheduledTaskTrigger { param([switch]$AtLogOn,$User) @{User=$User} }
function New-ScheduledTaskPrincipal { param($UserId,$LogonType,$RunLevel) if($RunLevel -ne 'Limited'){throw 'Startup must not elevate'}; @{UserId=$UserId} }
function New-ScheduledTaskSettingsSet { @{Test=$true} }
function Get-ScheduledTask { }
function Register-ScheduledTask { param($TaskName,$Action,$Trigger,$Principal,$Settings) if(-not $Action -or -not $Principal){throw 'Missing startup action/principal'}; $global:forgeLoopInstallerTaskCreated=$true }
function java {
    $global:LASTEXITCODE=0
    if($args -contains '--version') { 'openjdk 21.0.1'; return }
    if($args -contains 'register-stdin') {
        if($args -contains 'enrollment-placeholder') { throw 'Token leaked into command arguments' }
        if(($input | Out-String).Trim() -ne 'enrollment-placeholder') { throw 'Missing stdin token' }
        Set-Content -LiteralPath $env:FORGELOOP_RUNNER_STATE_FILE -Value 'test-identity'
    } elseif($args -contains 'serve') {
        if($env:ANTHROPIC_API_KEY -ne 'provider-placeholder') { throw 'Key was not decrypted into child environment' }
    } elseif($args -notcontains 'heartbeat') { throw 'Unexpected Java invocation' }
}
$previousIdentity=$env:FORGELOOP_RUNNER_STATE_FILE
$previousKey=$env:ANTHROPIC_API_KEY
try {
    & (Join-Path $testRoot 'Install-Runner.ps1') -StartAtLogin
    if(-not $global:forgeLoopInstallerTaskCreated) { throw 'Optional startup task was not registered' }
    $policy=Get-Content -LiteralPath (Join-Path $testRoot 'provider-policy.json') -Raw | ConvertFrom-Json
    if($policy.default.inputUsdPerMillion -ne 3 -or $policy.default.outputUsdPerMillion -ne 15) { throw 'Pricing was not saved' }
    $encrypted=Get-Content -LiteralPath (Join-Path $testRoot 'provider-key.xml') -Raw
    if($encrypted.Contains('provider-placeholder')) { throw 'Provider key persisted in cleartext' }
    & (Join-Path $testRoot 'Start-Runner.ps1')
    if($env:ANTHROPIC_API_KEY -ne $previousKey) { throw 'Process environment was not restored' }
    Write-Host 'Runner installer passed: stdin enrollment, encrypted credentials, pricing, heartbeat and startup.'
} finally {
    Remove-Variable forgeLoopInstallerTestAnswers -Scope Global
    Remove-Variable forgeLoopInstallerTaskCreated -Scope Global -ErrorAction SilentlyContinue
    $env:FORGELOOP_RUNNER_STATE_FILE=$previousIdentity
    $env:ANTHROPIC_API_KEY=$previousKey
    # Only the exact GUID-scoped test directory created above is removed.
    $resolved=[IO.Path]::GetFullPath($testRoot)
    $temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if($resolved.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -like 'forgeloop-installer-test-*') { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
