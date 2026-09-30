param([Parameter(Mandatory=$true)][ValidateSet('msi','dmg','deb','rpm','appimage')][string]$PackageType)
$ErrorActionPreference='Stop'
# Installation mutates only disposable hosted CI machines; never run on a user's workstation.
if ($env:GITHUB_ACTIONS -ne 'true') { throw 'Native installation smoke tests run only on disposable GitHub Actions hosts.' }
$assetExtension=if ($PackageType -eq 'appimage') { 'AppImage' } else { $PackageType }
$package=Get-ChildItem 'artifacts/native-installer/packages' -Filter "*.$assetExtension" | Select-Object -First 1
if (-not $package) { throw 'Native package is missing' }
if ($PackageType -eq 'msi') {
    $installDirectory=Join-Path $env:RUNNER_TEMP ('forgeloop-install-'+[guid]::NewGuid().ToString('N'))
    $install=Start-Process msiexec.exe -ArgumentList @('/i',('"'+$package.FullName+'"'),'/qn',('INSTALLDIR="'+$installDirectory+'"')) -Wait -PassThru -WindowStyle Hidden
    if ($install.ExitCode -notin @(0,3010)) { throw "MSI installation failed: $($install.ExitCode)" }
    try {
        $launcher=Join-Path $installDirectory 'ForgeLoop Runner.exe'
        if (-not (Test-Path -LiteralPath $launcher)) { throw 'Installed launcher missing' }
        # Verify the installed EXE resource, not just the input icon supplied to jpackage.
        Add-Type -AssemblyName System.Drawing
        $expectedIcon=[Drawing.Icon]::new((Join-Path (Get-Location) 'artifacts/native-installer/icons/forgeloop.ico'),32,32)
        $actualIcon=[Drawing.Icon]::ExtractAssociatedIcon($launcher)
        $expectedBitmap=$expectedIcon.ToBitmap()
        $actualBitmap=$actualIcon.ToBitmap()
        try {
            if ($actualBitmap.Size -ne $expectedBitmap.Size) { throw 'Installed icon size differs from the favicon' }
            for ($x=0; $x -lt $actualBitmap.Width; $x++) {
                for ($y=0; $y -lt $actualBitmap.Height; $y++) {
                    if ($actualBitmap.GetPixel($x,$y).ToArgb() -ne $expectedBitmap.GetPixel($x,$y).ToArgb()) { throw 'Installed launcher does not use the ForgeLoop favicon' }
                }
            }
        } finally { $expectedBitmap.Dispose(); $actualBitmap.Dispose(); $expectedIcon.Dispose(); $actualIcon.Dispose() }
        $check=Start-Process -FilePath $launcher -ArgumentList '--self-test' -Wait -PassThru -WindowStyle Hidden
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
        $expectedHash=(Get-FileHash 'artifacts/native-installer/icons/forgeloop.icns').Hash
        $matchingIcons=@(Get-ChildItem -LiteralPath (Join-Path $mount 'ForgeLoop Runner.app/Contents/Resources') -Filter '*.icns' | Where-Object { (Get-FileHash -LiteralPath $_.FullName).Hash -eq $expectedHash })
        if ($matchingIcons.Count -eq 0) { throw 'macOS package favicon is missing' }
        & (Join-Path $mount 'ForgeLoop Runner.app/Contents/MacOS/ForgeLoop Runner') --self-test
        if ($LASTEXITCODE -ne 0) { throw 'Packaged macOS app failed' }
    } finally { & hdiutil detach $mount }
} elseif ($PackageType -eq 'deb') {
    # Hosted Ubuntu is a minimal server image. Supply the standard desktop menu
    # directory that a desktop distribution provides before testing its shortcut.
    & sudo install -d /usr/share/desktop-directories
    if ($LASTEXITCODE -ne 0) { throw 'Desktop menu fixture setup failed' }
    & sudo apt-get install -y $package.FullName
    if ($LASTEXITCODE -ne 0) { throw 'DEB installation failed' }
    try {
        # jpackage nests the application beneath the configured installation root.
        # Resolve the executable from this package's inventory, not a guessed layout.
        $launchers=@((& dpkg-query -L forgeloop-runner) | Where-Object { $_.EndsWith('/bin/ForgeLoop Runner') })
        if ($launchers.Count -ne 1 -or -not $launchers[0].StartsWith('/opt/forgeloop-runner/')) { throw 'Unexpected installed package launcher layout' }
        $configuration=Join-Path (Split-Path (Split-Path $launchers[0] -Parent) -Parent) 'lib/app/ForgeLoop Runner.cfg'
        if (-not (Test-Path -LiteralPath $configuration) -or -not (Get-Content -LiteralPath $configuration -Raw).Contains('-Dforgeloop.desktop.package=deb')) { throw 'DEB updater target option is missing' }
        $expectedHash=(Get-FileHash 'artifacts/native-installer/icons/forgeloop.png').Hash
        $matchingIcons=@((& dpkg-query -L forgeloop-runner) | Where-Object { $_.EndsWith('.png') -and (Get-FileHash -LiteralPath $_).Hash -eq $expectedHash })
        if ($matchingIcons.Count -eq 0) { throw 'Linux package favicon is missing' }
        & $launchers[0] --self-test
        if ($LASTEXITCODE -ne 0) { throw 'Installed Linux launcher failed' }
    } finally { & sudo apt-get remove -y forgeloop-runner }
} elseif ($PackageType -eq 'rpm') {
    # CI is Ubuntu, so install without RPM dependency resolution after the Linux
    # keyring tools are provisioned explicitly above.
    & sudo rpm --install --nodeps $package.FullName
    if ($LASTEXITCODE -ne 0) { throw 'RPM installation failed' }
    try {
        $launchers=@((& rpm -ql forgeloop-runner) | Where-Object { $_.EndsWith('/bin/ForgeLoop Runner') })
        if ($launchers.Count -ne 1 -or -not $launchers[0].StartsWith('/opt/forgeloop-runner/')) { throw 'Unexpected installed RPM launcher layout' }
        $configuration=Join-Path (Split-Path (Split-Path $launchers[0] -Parent) -Parent) 'lib/app/ForgeLoop Runner.cfg'
        if (-not (Test-Path -LiteralPath $configuration) -or -not (Get-Content -LiteralPath $configuration -Raw).Contains('-Dforgeloop.desktop.package=rpm')) { throw 'RPM updater target option is missing' }
        $expectedHash=(Get-FileHash 'artifacts/native-installer/icons/forgeloop.png').Hash
        $matchingIcons=@((& rpm -ql forgeloop-runner) | Where-Object { $_.EndsWith('.png') -and (Test-Path -LiteralPath $_) -and (Get-FileHash -LiteralPath $_).Hash -eq $expectedHash })
        if ($matchingIcons.Count -eq 0) { throw 'RPM package favicon is missing' }
        & $launchers[0] --self-test
        if ($LASTEXITCODE -ne 0) { throw 'Installed RPM launcher failed' }
    } finally {
        & sudo rpm --erase forgeloop-runner
        if ($LASTEXITCODE -ne 0) { throw 'Test RPM uninstall failed' }
    }
} else {
    $configuration='artifacts/native-installer/appimage/ForgeLoop Runner.AppDir/lib/app/ForgeLoop Runner.cfg'
    if (-not (Test-Path -LiteralPath $configuration) -or -not (Get-Content -LiteralPath $configuration -Raw).Contains('-Dforgeloop.desktop.package=appimage')) { throw 'AppImage updater target option is missing' }
    $env:APPIMAGE_EXTRACT_AND_RUN='1'
    & $package.FullName --self-test
    if ($LASTEXITCODE -ne 0) { throw 'AppImage launcher failed' }
}
Write-Output 'Native package launch passed without enrollment or provider calls.'
