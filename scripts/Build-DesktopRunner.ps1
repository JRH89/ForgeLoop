param(
    [ValidateSet('app-image','msi','dmg','deb')][string]$PackageType='app-image',
    [string]$OutputDirectory='artifacts/desktop-runner',
    [string]$PackageVersion='1.0.1'
)
$ErrorActionPreference='Stop'
# Native packages are built on their target OS. jlink retains java for the worker child JVM.
$repository=Split-Path $PSScriptRoot -Parent
$output=[IO.Path]::GetFullPath((Join-Path $repository $OutputDirectory))
if (Test-Path -LiteralPath $output) { throw "Choose a fresh output directory: $output" }
$jar=Join-Path $repository 'runner/target/runner-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build and test runner with mvn verify first.' }
foreach ($tool in @('jlink','jpackage')) { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "A JDK with $tool is required to build (not to install)." } }
New-Item -ItemType Directory -Path $output | Out-Null
$inputDirectory=Join-Path $output 'input'
New-Item -ItemType Directory -Path $inputDirectory | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $inputDirectory 'runner.jar')
$runtime=Join-Path $output 'runtime'
& jlink --add-modules java.base,java.desktop,java.net.http,java.logging,java.management,java.naming,java.security.jgss,java.instrument,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Runtime build failed' }
# Apple's CFBundleVersion requires a positive first component. Installer revision
# is independent of the runner protocol version and is not a production-readiness claim.
$arguments=@('--type',$PackageType,'--name','ForgeLoop Runner','--app-version',$PackageVersion,'--vendor','Hooker Hill Studios','--description','Self-hosted ForgeLoop runner (development preview)','--input',$inputDirectory,'--main-jar','runner.jar','--main-class','io.forgeloop.runner.DesktopRunner','--runtime-image',$runtime,'--dest',(Join-Path $output 'packages'))
if ($PackageType -eq 'msi') { $arguments+=@('--win-per-user-install','--win-menu','--win-shortcut','--win-dir-chooser') }
if ($PackageType -eq 'dmg') { $arguments+=@('--mac-package-identifier','io.forgeloop.runner') }
if ($PackageType -eq 'deb') { $arguments+=@('--linux-shortcut','--linux-package-name','forgeloop-runner','--install-dir','/opt/forgeloop-runner','--linux-package-deps','libsecret-tools','--linux-deb-maintainer','support@hookerhillstudios.com') }
& jpackage @arguments
if ($LASTEXITCODE -ne 0) { throw 'Desktop packaging failed' }
Get-ChildItem -LiteralPath (Join-Path $output 'packages') -File | ForEach-Object {
    $digest=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$digest  $($_.Name)" | Set-Content -LiteralPath ($_.FullName+'.sha256') -Encoding ascii
}
Write-Output 'Development package built. Not signed or notarized; do not publish as a production installer.'
