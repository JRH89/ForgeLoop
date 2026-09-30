param(
    [ValidateSet('app-image','msi','dmg','deb','rpm','appimage')][string]$PackageType='app-image',
    [string]$OutputDirectory='artifacts/desktop-runner',
    [ValidatePattern('^[1-9][0-9]*\.[0-9]+\.[0-9]+$')][string]$PackageVersion='1.0.6',
    [string]$AppImageToolPath=$env:APPIMAGETOOL_PATH,
    [string]$AppImageRuntimePath=$env:APPIMAGETOOL_RUNTIME_PATH
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
# Apple's CFBundleVersion requires a positive first component. Installer revision
# is independent of the runner protocol version and is not a production-readiness claim.
$jpackageType=if ($PackageType -eq 'appimage') { 'app-image' } else { $PackageType }
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
if ($PackageType -eq 'appimage') {
    if (-not $IsLinux) { throw 'AppImage packages must be built on Linux.' }
    if (-not $AppImageToolPath -or -not (Test-Path -LiteralPath $AppImageToolPath)) { throw 'Set APPIMAGETOOL_PATH to the verified x86_64 appimagetool binary.' }
    if (-not $AppImageRuntimePath -or -not (Test-Path -LiteralPath $AppImageRuntimePath)) { throw 'Set APPIMAGETOOL_RUNTIME_PATH to the verified x86_64 AppImage runtime.' }

    # jpackage supplies the bundled JVM and launcher; AppRun is the relocatable AppImage entry point.
    $image=Join-Path (Join-Path $output 'packages') 'ForgeLoop Runner'
    if (-not (Test-Path -LiteralPath (Join-Path $image 'bin/ForgeLoop Runner'))) { throw 'jpackage app-image is missing its Linux launcher.' }
    $appDirectory=Join-Path (Join-Path $output 'appimage') 'ForgeLoop Runner.AppDir'
    New-Item -ItemType Directory -Path $appDirectory -Force | Out-Null
    Get-ChildItem -LiteralPath $image -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $appDirectory -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $appDirectory 'usr/share/applications') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $appDirectory 'usr/share/icons/hicolor/256x256/apps') -Force | Out-Null
    Copy-Item -LiteralPath $icon -Destination (Join-Path $appDirectory 'forgeloop.png')
    Copy-Item -LiteralPath $icon -Destination (Join-Path $appDirectory 'usr/share/icons/hicolor/256x256/apps/forgeloop.png')
    $appRun=@'
#!/bin/sh
set -eu
APPDIR="${APPDIR:-$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)}"
exec "$APPDIR/bin/ForgeLoop Runner" "$@"
'@
    $desktop=@'
[Desktop Entry]
Name=ForgeLoop Runner
Comment=Run ForgeLoop tasks on infrastructure you control
Exec=AppRun %U
Icon=forgeloop
Type=Application
Terminal=false
Categories=Development;Utility;
'@
    [IO.File]::WriteAllText((Join-Path $appDirectory 'AppRun'),$appRun.TrimStart(),[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $appDirectory 'forgeloop.desktop'),$desktop.TrimStart(),[Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath (Join-Path $appDirectory 'forgeloop.desktop') -Destination (Join-Path $appDirectory 'usr/share/applications/forgeloop.desktop')
    & chmod 755 (Join-Path $appDirectory 'AppRun')
    if ($LASTEXITCODE -ne 0) { throw 'Could not mark the AppImage entry point executable.' }
    $appImage=Join-Path (Join-Path $output 'packages') "forgeloop-runner-$PackageVersion-linux-x64.AppImage"
    $previousArchitecture=$env:ARCH
    $previousExtractMode=$env:APPIMAGE_EXTRACT_AND_RUN
    try {
        $env:ARCH='x86_64'
        $env:APPIMAGE_EXTRACT_AND_RUN='1'
        & $AppImageToolPath --runtime-file $AppImageRuntimePath $appDirectory $appImage
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $appImage)) { throw 'AppImage creation failed.' }
    } finally {
        $env:ARCH=$previousArchitecture
        $env:APPIMAGE_EXTRACT_AND_RUN=$previousExtractMode
    }
}
Get-ChildItem -LiteralPath (Join-Path $output 'packages') -File | ForEach-Object {
    $digest=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$digest  $($_.Name)" | Set-Content -LiteralPath ($_.FullName+'.sha256') -Encoding ascii
}
Write-Output 'Development package built. Not signed or notarized; do not publish as a production installer.'
