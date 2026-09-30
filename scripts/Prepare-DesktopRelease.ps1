param(
    [Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]*\.[0-9]+\.[0-9]+$')][string]$Version,
    [Parameter(Mandatory)][ValidateSet('windows','macos','linux')][string]$Platform,
    [Parameter(Mandatory)][ValidateSet('x64','arm64')][string]$Architecture,
    [Parameter(Mandatory)][ValidateSet('msi','dmg','deb','rpm','appimage')][string]$PackageType
)
$ErrorActionPreference='Stop'
$allowedTypes=@{windows=@('msi');macos=@('dmg');linux=@('deb','rpm','appimage')}[$Platform]
if ($PackageType -notin $allowedTypes) { throw 'Installer type does not match platform.' }
# Verify the actual build host; never label an Intel binary as an ARM package.
$actualArchitecture=[Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
if ($Architecture -ne $actualArchitecture) { throw "Expected $Architecture build host, got $actualArchitecture" }
$assetExtension=if ($PackageType -eq 'appimage') { 'AppImage' } else { $PackageType }
$packages=@(Get-ChildItem -LiteralPath 'artifacts/native-installer/packages' -Filter "*.$assetExtension" -File)
if ($packages.Count -ne 1) { throw 'Expected exactly one verified native installer.' }
$package=$packages[0]
$hash=(Get-FileHash -LiteralPath $package.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$originalChecksum=(Get-Content -LiteralPath ($package.FullName+'.sha256') -Raw).Trim().Split(' ')[0]
if ($hash -ne $originalChecksum) { throw 'Installer checksum changed after packaging.' }
$destination='artifacts/release'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$name="forgeloop-runner-$Version-$Platform-$Architecture.$assetExtension"
Copy-Item -LiteralPath $package.FullName -Destination (Join-Path $destination $name)
"$hash  $name" | Set-Content -LiteralPath (Join-Path $destination "$name.sha256") -Encoding ascii
