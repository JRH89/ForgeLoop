[CmdletBinding()]
param(
    [string]$CredentialsFile = "$env:USERPROFILE\.cloudflared\e0c976c6-b8ab-4f1a-8799-96e365adba3d.json"
)

$ErrorActionPreference = "Stop"

$resolvedCredentials = (Resolve-Path -LiteralPath $CredentialsFile).Path
$env:CLOUDFLARED_CREDENTIALS_FILE = $resolvedCredentials

# The tunnel runs in Compose so the same checked-in configuration works on a
# developer workstation and on the eventual production host.
docker compose --profile tunnel up -d cloudflared
docker compose --profile tunnel ps cloudflared
