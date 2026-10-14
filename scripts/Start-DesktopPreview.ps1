param(
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$MavenCommand='mvn.cmd'
)
$ErrorActionPreference='Stop'
$previewRoot=Split-Path $PSScriptRoot -Parent
$runnerPath=Join-Path $previewRoot 'runner'
if (!$JavaHome) { throw 'Supply -JavaHome pointing to a JDK 21 installation.' }
$env:JAVA_HOME=$JavaHome
Push-Location $runnerPath
try {
    # Compile only; never invoke jpackage, install, release, or an authenticated runner.
    & $MavenCommand -q test-compile dependency:build-classpath '-Dmdep.outputFile=target/preview-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Preview compilation failed.' }
    $previewClasspath=(Join-Path $runnerPath 'target/test-classes')+';'+(Join-Path $runnerPath 'target/classes')+';'+(Get-Content 'target/preview-classpath.txt' -Raw).Trim()
    Start-Process -FilePath (Join-Path $JavaHome 'bin/javaw.exe') -ArgumentList @('-cp',('"'+$previewClasspath+'"'),'io.forgeloop.runner.DesktopPreview') -WindowStyle Normal
} finally { Pop-Location }
