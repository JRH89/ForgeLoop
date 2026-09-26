param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$configuration = Get-Content -LiteralPath (Join-Path $root 'runner-config.json') -Raw | ConvertFrom-Json
$identity = Join-Path $root 'identity'
$jar = Join-Path $root 'runner.jar'
if ($CheckOnly) {
    & java -cp $jar io.forgeloop.runner.RunnerMain heartbeat $configuration.endpoint $identity
    if ($LASTEXITCODE -ne 0) { throw 'Runner heartbeat failed.' }
    return
}
# Prevent two workers from using the same identity/worktree state on this host.
$lock = $null
$variable = switch ($configuration.provider) { 'anthropic' {'ANTHROPIC_API_KEY'} 'openai' {'OPENAI_API_KEY'} 'gemini' {'GEMINI_API_KEY'} default {throw 'Invalid provider configuration'} }
$previous = [Environment]::GetEnvironmentVariable($variable, 'Process')
try {
    $lock = [IO.File]::Open((Join-Path $root 'worker.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $secureKey = Import-Clixml -LiteralPath (Join-Path $root 'provider-key.xml')
    [Environment]::SetEnvironmentVariable($variable, [System.Net.NetworkCredential]::new('', $secureKey).Password, 'Process')
    Remove-Variable secureKey
    & java -cp $jar io.forgeloop.runner.RunnerMain serve $configuration.endpoint $identity (Join-Path $root 'repositories') (Join-Path $root 'worktrees') (Join-Path $root 'provider-policy.json') 'src' (Join-Path $root 'leases') 1
    if ($LASTEXITCODE -ne 0) { throw "Runner exited with code $LASTEXITCODE" }
} finally {
    [Environment]::SetEnvironmentVariable($variable, $previous, 'Process')
    if ($lock) { $lock.Dispose() }
}
