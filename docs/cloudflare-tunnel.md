# Cloudflare Tunnel

ForgeLoop uses the named Cloudflare Tunnel `forgeloop`
(`e0c976c6-b8ab-4f1a-8799-96e365adba3d`) for the stable hostname
`forgeloop.hookerhillstudios.com`.

Only the GitHub App webhook and installation callback are exposed. The edge
proxy returns `404` for GraphQL, actuator endpoints, the operator UI, and all
other paths. Keep operator and runner APIs behind their authenticated network
boundary.

## Start on this workstation

The account-scoped `cert.pem` is needed only for tunnel administration. Runtime
uses the tunnel-specific JSON credentials file and neither file belongs in Git.

```powershell
.\scripts\Start-ForgeLoopTunnel.ps1
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

The first request must return `404`. The unsigned webhook request must reach
ForgeLoop but be rejected (`400` or `401`), proving the route is live without
bypassing signature validation.
