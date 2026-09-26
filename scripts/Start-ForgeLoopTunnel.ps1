[CmdletBinding()]
param(
    [string]$CredentialsFile = "$env:USERPROFILE\.cloudflared\e0c976c6-b8ab-4f1a-8799-96e365adba3d.json"
)

$ErrorActionPreference = "Stop"

$windowsConnector = Get-Service -Name "Cloudflared" -ErrorAction SilentlyContinue
if ($windowsConnector -and $windowsConnector.Status -eq "Running") {
    throw "The Windows Cloudflared service is already running. Stop and disable it from an elevated PowerShell before starting the Compose connector."
}

$resolvedCredentials = (Resolve-Path -LiteralPath $CredentialsFile).Path
$env:CLOUDFLARED_CREDENTIALS_FILE = $resolvedCredentials

# The tunnel runs in Compose so the same checked-in configuration works on a
# developer workstation and on the eventual production host.
# Recreate so ingress changes are loaded; cloudflared does not hot-reload this file.
docker compose --profile tunnel up -d --force-recreate cloudflared
docker compose --profile tunnel ps cloudflared
