# Cloudflare Tunnel

ForgeLoop uses the named Cloudflare Tunnel `forgeloop`
(`e0c976c6-b8ab-4f1a-8799-96e365adba3d`) for the stable hostname
`forgeloop.hookerhillstudios.com`.

The hostname now serves the public landing page and routes same-origin GitHub
OAuth, signed webhooks, installation callbacks, and the authenticated operator
console through the web gateway. The landing page loads no tenant data.
GraphQL operator access is enforced by persisted organization membership;
runner mutations retain their independent runner and lease credentials.

## Start on this workstation

The account-scoped `cert.pem` is needed only for tunnel administration. Runtime
uses the tunnel-specific JSON credentials file and neither file belongs in Git.

```powershell
.\scripts\Start-ForgeLoopTunnel.ps1
```

Run exactly one connector per workstation. If the Windows `Cloudflared` service
was installed earlier, stop and disable it from an elevated PowerShell before
using the Compose connector; two connectors with different origin
configurations cause intermittent `503` responses:

```powershell
Stop-Service Cloudflared
Set-Service Cloudflared -StartupType Disabled
```

Configure the GitHub App with:

- Webhook URL: `https://forgeloop.hookerhillstudios.com/api/github/webhooks`
- Setup URL: `https://forgeloop.hookerhillstudios.com/api/github/app/callback`

The checked-in helper updates the webhook URL and preserves the secret from the
ignored `.env` file without printing either credential:

```powershell
node .\scripts\Update-GithubAppWebhook.mjs `
  https://forgeloop.hookerhillstudios.com/api/github/webhooks
```

Inspect recent delivery status without printing payloads or signatures, and
redeliver an exact failed delivery ID when the local stack was unavailable:

```powershell
node .\scripts\Update-GithubAppWebhook.mjs --deliveries
node .\scripts\Update-GithubAppWebhook.mjs --redeliver <delivery-id>
```

GitHub does not automatically retry failed webhook deliveries, so production
operations must schedule bounded redelivery reconciliation rather than relying
on an operator to notice a failed delivery.

GitHub does not expose setup-URL changes through the webhook configuration API;
set that value in the GitHub App settings page.

## Move to the production server

1. Install Docker and clone ForgeLoop on the server.
2. Transfer the tunnel-specific JSON credentials through the deployment secret
   manager. Do not copy `cert.pem`; the server does not need account-wide tunnel
   administration access.
3. Start the stack with the credential's absolute server path:

   ```bash
   CLOUDFLARED_CREDENTIALS_FILE=/run/secrets/forgeloop-tunnel.json \
     docker compose --profile tunnel up -d cloudflared
   ```

4. Confirm the server replica is connected before stopping the workstation
   replica. The hostname and GitHub App settings do not change because both
   replicas use the same tunnel UUID.
5. Remove the workstation's tunnel credential after cutover if it no longer
   runs a replica.

## Verification

```powershell
curl.exe -i https://forgeloop.hookerhillstudios.com/graphql
curl.exe -i -X POST https://forgeloop.hookerhillstudios.com/api/github/webhooks `
  -H "Content-Type: application/json" -d "{}"
docker compose --profile tunnel logs cloudflared
```

The root request must return `200`, `/oauth2/authorization/github` must redirect
to GitHub, and the unsigned webhook request must be rejected (`400` or `401`).
Together these prove the public shell and both independent authentication
boundaries are live.
