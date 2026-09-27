param([string]$BaselineDirectory='artifacts/desktop-baseline/packages',[string]$UpdateDirectory='artifacts/native-installer/packages')
$ErrorActionPreference='Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or !$IsWindows) { throw 'Upgrade tests are restricted to disposable Windows CI hosts.' }
$baseline=Get-ChildItem -LiteralPath $BaselineDirectory -Filter '*.msi' | Select-Object -First 1
$update=Get-ChildItem -LiteralPath $UpdateDirectory -Filter '*.msi' | Select-Object -First 1
if (!$baseline -or !$update) { throw 'Both installer revisions are required.' }
$stateDirectory=Join-Path $env:USERPROFILE '.forgeloop/desktop-runner'
if (Test-Path -LiteralPath $stateDirectory) { throw 'Refusing to touch pre-existing runner state.' }
$installDirectory=Join-Path $env:RUNNER_TEMP ('forgeloop-upgrade-'+[guid]::NewGuid().ToString('N'))
function Install-Package($package) {
    $process=Start-Process msiexec.exe -ArgumentList @('/i',('"'+$package.FullName+'"'),'/qn',('INSTALLDIR="'+$installDirectory+'"')) -Wait -PassThru -WindowStyle Hidden
    if ($process.ExitCode -notin @(0,3010)) { throw "Install failed: $($process.ExitCode)" }
}
function Assert-StatePreserved {
    foreach ($item in $script:expected.GetEnumerator()) {
        if (!(Test-Path -LiteralPath $item.Key) -or (Get-FileHash -LiteralPath $item.Key).Hash -ne $item.Value) { throw 'Installer modified or removed saved runner state.' }
    }
}
# Fake fixture only: no network, repository, or usable model credential.
New-Item -ItemType Directory -Path $stateDirectory | Out-Null
Set-Content -LiteralPath (Join-Path $stateDirectory 'identity') -Value "fake-runner`nfake-enrollment" -NoNewline
Set-Content -LiteralPath (Join-Path $stateDirectory 'config.json') -Value '{"endpoint":"https://example.invalid","provider":"anthropic","model":"fake","inputUsdPerMillion":1,"outputUsdPerMillion":1,"startAtLogin":false}' -NoNewline
Set-Content -LiteralPath (Join-Path $stateDirectory 'runner-name') -Value 'Upgrade regression runner' -NoNewline
$fakeKey=[Text.Encoding]::UTF8.GetBytes('fake-upgrade-test-key')
$encryptedKey=[Security.Cryptography.ProtectedData]::Protect($fakeKey,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
[IO.File]::WriteAllBytes((Join-Path $stateDirectory 'anthropic-key.dpapi'),$encryptedKey)
$script:expected=@{}
Get-ChildItem -LiteralPath $stateDirectory -File | ForEach-Object { $script:expected[$_.FullName]=(Get-FileHash -LiteralPath $_.FullName).Hash }
try {
    Install-Package $baseline
    Install-Package $update
    Assert-StatePreserved
    $launcher=Join-Path $installDirectory 'ForgeLoop Runner.exe'
    if (!(Test-Path -LiteralPath $launcher)) { throw 'Upgraded launcher missing.' }
    $version=[Diagnostics.FileVersionInfo]::GetVersionInfo($launcher).ProductVersion
    if ($version -notlike '1.0.2*') { throw "Expected upgraded launcher 1.0.2, got $version" }
    $remove=Start-Process msiexec.exe -ArgumentList @('/x',('"'+$update.FullName+'"'),'/qn') -Wait -PassThru -WindowStyle Hidden
    if ($remove.ExitCode -notin @(0,3010)) { throw 'Uninstall failed.' }
    Assert-StatePreserved
    Install-Package $update
    Assert-StatePreserved
    Write-Output 'PASS: upgrade and uninstall/reinstall preserve saved runner identity, name and settings.'
} finally {
    # Only this exact CI-owned installer and explicitly listed fake fixture files are removed.
    Start-Process msiexec.exe -ArgumentList @('/x',('"'+$update.FullName+'"'),'/qn') -Wait -WindowStyle Hidden
    foreach ($path in $script:expected.Keys) { Remove-Item -LiteralPath $path }
    Remove-Item -LiteralPath $stateDirectory
}
