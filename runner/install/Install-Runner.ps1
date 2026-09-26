param([switch]$StartAtLogin)
$ErrorActionPreference = 'Stop'

# The package remains in this private folder; only encrypted provider secrets are persisted.
foreach ($command in @('java', 'git', 'docker')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "Install $command before running setup. See README.md." }
}
$dockerType = docker info --format '{{.OSType}}'
if ($LASTEXITCODE -ne 0 -or $dockerType.Trim() -ne 'linux') { throw 'Start Docker with Linux containers before setup.' }
$version = (& java --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $version -notmatch '(?:openjdk|java) (\d+)' -or [int]$Matches[1] -lt 21) { throw 'Java 21 or newer is required.' }
$root = $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $root 'runner.jar'))) { throw 'Extract the entire runner package first.' }
# Restrict this installation to its current Windows user; DPAPI also binds credentials to that user.
$sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
icacls.exe $root /inheritance:r /grant:r "*${sid}:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict runner directory permissions.' }
$endpoint = Read-Host 'ForgeLoop URL (for example https://forgeloop.hookerhillstudios.com)'
$uri = [Uri]$endpoint
if (-not $uri.IsAbsoluteUri -or ($uri.Scheme -ne 'https' -and -not ($uri.Scheme -eq 'http' -and $uri.IsLoopback)) -or $uri.UserInfo) { throw 'Use HTTPS, or HTTP on localhost for development.' }
$endpoint = $endpoint.TrimEnd('/')
$env:FORGELOOP_RUNNER_STATE_FILE = Join-Path $root 'identity'
if (-not (Test-Path -LiteralPath $env:FORGELOOP_RUNNER_STATE_FILE)) {
    $name = Read-Host 'Runner name'
    $secureToken = Read-Host 'One-time enrollment token from Harness & policy' -AsSecureString
    try {
        [System.Net.NetworkCredential]::new('', $secureToken).Password | & java -cp (Join-Path $root 'runner.jar') io.forgeloop.runner.RunnerMain register-stdin $endpoint $name 'git,provider,docker'
        if ($LASTEXITCODE -ne 0) { throw 'Enrollment failed. Request a fresh token if it expired.' }
    } finally { Remove-Variable secureToken -ErrorAction SilentlyContinue }
} else { Write-Host 'Reusing this installation identity; not re-enrolling.' }
$provider = Read-Host 'Provider (anthropic, openai, gemini)'
if ($provider -notin @('anthropic','openai','gemini')) { throw 'Choose one supported provider.' }
$model = Read-Host 'Exact model ID enabled on your provider account'
if ([string]::IsNullOrWhiteSpace($model)) { throw 'Model ID is required.' }
function Read-Rate([string]$label) {
    $value = Read-Host $label
    $rate = [decimal]0
    if (-not [decimal]::TryParse($value, [Globalization.NumberStyles]::Number, [Globalization.CultureInfo]::InvariantCulture, [ref]$rate) -or $rate -lt 0) { throw 'Enter a nonnegative USD price using a decimal point.' }
    return $rate
}
$inputRate = Read-Rate 'Input USD per million tokens (from your provider pricing)'
$outputRate = Read-Rate 'Output USD per million tokens (from your provider pricing)'
$apiKey = Read-Host 'Provider API key (encrypted for your Windows user)' -AsSecureString
if ($apiKey.Length -eq 0) { throw 'Provider key is required.' }
try { $apiKey | Export-Clixml -LiteralPath (Join-Path $root 'provider-key.xml') }
finally { Remove-Variable apiKey }
@{ default = @{ provider=$provider; model=$model; maxAttempts=2; inputUsdPerMillion=$inputRate; outputUsdPerMillion=$outputRate } } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $root 'provider-policy.json') -Encoding UTF8
@{ endpoint=$endpoint; provider=$provider } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'runner-config.json') -Encoding UTF8
& (Join-Path $root 'Start-Runner.ps1') -CheckOnly
if ($StartAtLogin) {
    $script = Join-Path $root 'Start-Runner.ps1'
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`"" -WorkingDirectory $root
    $user = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
    $principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -StartWhenAvailable
    $taskName = 'ForgeLoop Runner ' + (Split-Path $root -Leaf)
    if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { throw "Scheduled task $taskName already exists; review it before replacing it." }
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings | Out-Null
    Write-Host "Start-at-login installed as '$taskName'. Docker must also start at login."
}
Write-Host 'Setup complete. No paid provider request was made. Run Start-Runner.ps1 when ready to process issues.'
