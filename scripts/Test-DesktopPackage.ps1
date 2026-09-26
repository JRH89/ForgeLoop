param([Parameter(Mandatory=$true)][ValidateSet('msi','dmg','deb')][string]$PackageType)
$ErrorActionPreference='Stop'
# Installation mutates only disposable hosted CI machines; never run on a user's workstation.
if ($env:GITHUB_ACTIONS -ne 'true') { throw 'Native installation smoke tests run only on disposable GitHub Actions hosts.' }
$package=Get-ChildItem 'artifacts/native-installer/packages' -Filter "*.$PackageType" | Select-Object -First 1
if (-not $package) { throw 'Native package is missing' }
if ($PackageType -eq 'msi') {
    $installDirectory=Join-Path $env:RUNNER_TEMP ('forgeloop-install-'+[guid]::NewGuid().ToString('N'))
    $install=Start-Process msiexec.exe -ArgumentList @('/i',('"'+$package.FullName+'"'),'/qn',('INSTALLDIR="'+$installDirectory+'"')) -Wait -PassThru -WindowStyle Hidden
    if ($install.ExitCode -notin @(0,3010)) { throw "MSI installation failed: $($install.ExitCode)" }
    try {
        $launcher=Join-Path $installDirectory 'ForgeLoop Runner.exe'
        if (-not (Test-Path -LiteralPath $launcher)) { throw 'Installed launcher missing' }
        $check=Start-Process -FilePath $launcher -ArgumentList '--version' -Wait -PassThru -WindowStyle Hidden
        if ($check.ExitCode -ne 0) { throw 'Installed Windows launcher failed' }
    } finally {
        $remove=Start-Process msiexec.exe -ArgumentList @('/x',('"'+$package.FullName+'"'),'/qn') -Wait -PassThru -WindowStyle Hidden
        if ($remove.ExitCode -notin @(0,3010)) { throw 'Test MSI uninstall failed' }
    }
} elseif ($PackageType -eq 'dmg') {
    $mount=Join-Path $env:RUNNER_TEMP ('forgeloop-dmg-'+[guid]::NewGuid().ToString('N'))
    & hdiutil attach -nobrowse -mountpoint $mount $package.FullName
    if ($LASTEXITCODE -ne 0) { throw 'DMG mount failed' }
    try {
        & (Join-Path $mount 'ForgeLoop Runner.app/Contents/MacOS/ForgeLoop Runner') --version
        if ($LASTEXITCODE -ne 0) { throw 'Packaged macOS app failed' }
    } finally { & hdiutil detach $mount }
} else {
    # Hosted Ubuntu is a minimal server image. Supply the standard desktop menu
    # directory that a desktop distribution provides before testing its shortcut.
    & sudo install -d /usr/share/desktop-directories
    if ($LASTEXITCODE -ne 0) { throw 'Desktop menu fixture setup failed' }
    & sudo apt-get install -y $package.FullName
    if ($LASTEXITCODE -ne 0) { throw 'DEB installation failed' }
    try {
        & '/opt/forgeloop-runner/bin/ForgeLoop Runner' --version
        if ($LASTEXITCODE -ne 0) { throw 'Installed Linux launcher failed' }
    } finally { & sudo apt-get remove -y forgeloop-runner }
}
Write-Output 'Native package launch passed without enrollment or provider calls.'
