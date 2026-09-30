param(
    [ValidateSet('app-image','msi','dmg','deb','rpm','tar.gz','pkg.tar.zst')][string]$PackageType='app-image',
    [string]$OutputDirectory='artifacts/desktop-runner',
    [ValidatePattern('^[1-9][0-9]*\.[0-9]+\.[0-9]+$')][string]$PackageVersion='1.0.6'
)
$ErrorActionPreference='Stop'
# Native packages are built on their target OS. jlink retains java for the worker child JVM.
$repository=Split-Path $PSScriptRoot -Parent
$output=[IO.Path]::GetFullPath((Join-Path $repository $OutputDirectory))
if (Test-Path -LiteralPath $output) { throw "Choose a fresh output directory: $output" }
$jar=Join-Path $repository 'runner/target/runner-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build and test runner with mvn verify first.' }
foreach ($tool in @('java','jlink','jpackage')) { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "A JDK with $tool is required to build (not to install)." } }
New-Item -ItemType Directory -Path $output | Out-Null
$inputDirectory=Join-Path $output 'input'
New-Item -ItemType Directory -Path $inputDirectory | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $inputDirectory 'runner.jar')
$icons=Join-Path $output 'icons'
& java '-Djava.awt.headless=true' -cp $jar io.forgeloop.runner.DesktopIcons $icons
if ($LASTEXITCODE -ne 0) { throw 'Favicon conversion failed' }
$extension=if ($IsWindows -or $env:OS -eq 'Windows_NT') { 'ico' } elseif ($IsMacOS) { 'icns' } else { 'png' }
$icon=Join-Path $icons ('forgeloop.'+$extension)
if (-not (Test-Path -LiteralPath $icon)) { throw 'Native launcher icon is missing' }
$runtime=Join-Path $output 'runtime'
& jlink --add-modules java.base,java.desktop,java.net.http,java.logging,java.management,java.naming,java.security.jgss,java.instrument,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Runtime build failed' }
# Portable and Arch packages use jpackage's native app-image as their payload.
# Apple's CFBundleVersion requires a positive first component; the installer
# revision is not a production-readiness claim.
$jpackageType=if ($PackageType -in @('tar.gz','pkg.tar.zst')) { 'app-image' } else { $PackageType }
$arguments=@('--type',$jpackageType,'--name','ForgeLoop Runner','--app-version',$PackageVersion,'--java-options',"-Dforgeloop.desktop.version=$PackageVersion",'--vendor','Hooker Hill Studios','--description','Self-hosted ForgeLoop runner (development preview)','--input',$inputDirectory,'--main-jar','runner.jar','--main-class','io.forgeloop.runner.DesktopRunner','--runtime-image',$runtime,'--dest',(Join-Path $output 'packages'))
if ($PackageType -ne 'app-image') { $arguments+=@('--java-options',"-Dforgeloop.desktop.package=$PackageType") }
$arguments+=@('--icon',$icon)
if ($PackageType -eq 'msi') { $arguments+=@('--win-per-user-install','--win-menu','--win-shortcut','--win-dir-chooser') }
if ($PackageType -eq 'dmg') { $arguments+=@('--mac-package-identifier','io.forgeloop.runner') }
if ($PackageType -in @('deb','rpm')) {
    $dependency=if ($PackageType -eq 'deb') { 'libsecret-tools' } else { 'libsecret' }
    $arguments+=@('--linux-shortcut','--linux-package-name','forgeloop-runner','--install-dir','/opt/forgeloop-runner','--linux-package-deps',$dependency)
    if ($PackageType -eq 'deb') { $arguments+=@('--linux-deb-maintainer','support@hookerhillstudios.com') }
}
& jpackage @arguments
if ($LASTEXITCODE -ne 0) { throw 'Desktop packaging failed' }
if ($PackageType -in @('tar.gz','pkg.tar.zst')) {
    if (-not $IsLinux) { throw 'Portable Linux packages must be built on Linux.' }
    $image=Join-Path (Join-Path $output 'packages') 'ForgeLoop Runner'
    $launcher=Join-Path $image 'bin/ForgeLoop Runner'
    if (-not (Test-Path -LiteralPath $launcher)) { throw 'jpackage app-image is missing its Linux launcher.' }
    $architecture=[Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
    if ($architecture -eq 'x64') { $architecture='x64' }
    elseif ($architecture -eq 'arm64') { $architecture='arm64' }
    else { throw "Unsupported Linux architecture: $architecture" }

    if ($PackageType -eq 'tar.gz') {
        $portableRoot=Join-Path $output 'portable/forgeloop-runner'
        $appDirectory=Join-Path $portableRoot 'ForgeLoop Runner'
        New-Item -ItemType Directory -Path $appDirectory -Force | Out-Null
        Get-ChildItem -LiteralPath $image -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $appDirectory -Recurse -Force }
        $runScript=@'
#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$ROOT/ForgeLoop Runner/bin/ForgeLoop Runner" "$@"
'@
        $readme=@'
ForgeLoop Runner portable Linux build

Extract this archive and launch it with ./run-forgeloop-runner.sh. Java is
bundled. Git and Docker are required for repository work. Linux credential
storage requires secret-tool and an unlocked Secret Service keyring.
'@
        [IO.File]::WriteAllText((Join-Path $portableRoot 'run-forgeloop-runner.sh'),$runScript.TrimStart(),[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $portableRoot 'README.txt'),$readme.TrimStart(),[Text.UTF8Encoding]::new($false))
        & chmod 755 (Join-Path $portableRoot 'run-forgeloop-runner.sh')
        if ($LASTEXITCODE -ne 0) { throw 'Could not mark the portable launcher executable.' }
        $archive=Join-Path (Join-Path $output 'packages') "forgeloop-runner-$PackageVersion-linux-$architecture.tar.gz"
        & tar -czf $archive -C (Split-Path $portableRoot -Parent) 'forgeloop-runner'
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $archive)) { throw 'Portable Linux archive creation failed.' }
    } else {
        $archBuilder=Join-Path $repository 'scripts/Build-ArchPackage.sh'
        if (-not (Test-Path -LiteralPath $archBuilder)) { throw 'Arch package builder script is missing.' }
        & bash $archBuilder $image $icon $PackageVersion $architecture (Join-Path $output 'packages')
        if ($LASTEXITCODE -ne 0) { throw 'Arch package build failed.' }
    }
}
Get-ChildItem -LiteralPath (Join-Path $output 'packages') -File | ForEach-Object {
    $digest=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$digest  $($_.Name)" | Set-Content -LiteralPath ($_.FullName+'.sha256') -Encoding ascii
}
Write-Output 'Development package built. Not signed or notarized; do not publish as a production installer.'
