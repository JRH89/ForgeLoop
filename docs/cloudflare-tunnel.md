# Linux server deployment with Cloudflare Tunnel

This guide deploys the current single-host Docker Compose stack at
`https://forgeloop.hookerhillstudios.com`, with
`https://forgeloop.codefrontlabs.com` as an additional hostname on the same
Cloudflare Tunnel. The Hooker Hill Studios hostname remains the configured
canonical URL. Cloudflare Tunnel is the only public ingress. PostgreSQL, the
control-plane API, and the web UI publish diagnostic ports on loopback only; they
are not directly reachable from the LAN or Internet.

The Compose deployment uses GitHub OAuth, a local PostgreSQL volume, and a local
artifact volume. It is a single-server deployment, not the separately managed
OIDC/S3 production profile described in [operations](operations.md). Its scheduled
backups are local to the server until an operator copies them to encrypted,
off-host storage.

## Prerequisites

- A Linux server with Docker Engine and the Docker Compose plugin, Git, OpenSSL,
  and SSH access. Keep Docker Engine enabled at boot.
- Outbound HTTPS access for image pulls and Cloudflare Tunnel. No inbound web
  ports are required; restrict SSH to trusted addresses.
- The named Cloudflare Tunnel `forgeloop` with UUID
  `e0c976c6-b8ab-4f1a-8799-96e365adba3d`, and its tunnel-specific JSON
  credentials. Cloudflare DNS must route both
  `forgeloop.hookerhillstudios.com` and `forgeloop.codefrontlabs.com` to this
  tunnel. The additional DNS route can be created with
  `cloudflared tunnel route dns forgeloop forgeloop.codefrontlabs.com`.
- A configured ForgeLoop GitHub App and OAuth client. See
  [deployment prerequisites](deployment-prerequisites.md) and
  [GitHub authentication](github-user-authentication.md).

Install Docker Engine and the Compose plugin using the
[Docker installation instructions for your Linux distribution](https://docs.docker.com/engine/install/).
For Arch/Omarchy and other derivatives, use the distribution's maintained
packages and instructions; Docker notes its platform instructions are not
tested or verified on distribution derivatives.

Check the host tools:

```bash
docker --version
docker compose version
git --version
openssl version
```

Do not expose ports 5432, 8090, or 5173 in the server firewall or cloud firewall.
Compose binds them to `127.0.0.1` for local diagnostics only. The tunnel reaches
the application through the private Compose network.
If a default diagnostic port is occupied, set `FORGELOOP_POSTGRES_HOST_PORT`,
`FORGELOOP_API_HOST_PORT`, `FORGELOOP_WEB_HOST_PORT`, or
`FORGELOOP_WEBHOOK_HOST_PORT` in the server `.env` to unused ports. These
settings change only the host-side loopback bindings; container ports and the
tunnel's internal service addresses do not change.

## Install a new server

1. Enable Docker at boot, clone the repository, and prepare its private config:

   ```bash
   sudo systemctl enable --now docker
   sudo mkdir -p /opt/forgeloop
   sudo chown "$USER" /opt/forgeloop
   git clone https://github.com/JRH89/ForgeLoop.git /opt/forgeloop
   cd /opt/forgeloop
   cp -n .env.example .env
   chmod 600 .env
   ```

   To reuse the configured workstation environment, run these from PowerShell
   in the repository checkout (replace the placeholders with your Linux login,
   server name, and actual tunnel JSON path):

   ```powershell
   scp .env linux-user@server-host:forgeloop.env
   scp "C:\path\to\tunnel-credentials.json" linux-user@server-host:forgeloop-tunnel.json
   ```

   Then on the Linux server, restrict the transfer copies and install the
   transferred environment file over the sample:

   ```bash
   chmod 600 "$HOME/forgeloop.env" "$HOME/forgeloop-tunnel.json"
   sudo install -o root -g root -m 600 "$HOME/forgeloop.env" /opt/forgeloop/.env
   ```

2. Generate a unique database password. Keep the output private and put it in
   `FORGELOOP_DATABASE_PASSWORD` in `.env`. If you copied the workstation
   `.env`, use `sudoedit /opt/forgeloop/.env` to set it and all server-specific
   values; if using the sample, edit `.env` directly:

   ```bash
   openssl rand -hex 32
   ```

   This value is required; Compose intentionally refuses to start without it.
   PostgreSQL and Spring use the same value. Do not reuse `forgeloop`, a GitHub
   credential, or a runner/provider API key.

3. Set the server-specific values in `.env` using a secure editor such as
   `sudoedit .env`:

   ```dotenv
   FORGELOOP_SECURITY_MODE=github
   FORGELOOP_PUBLIC_BASE_URL=https://forgeloop.hookerhillstudios.com
   CLOUDFLARED_CREDENTIALS_FILE=/etc/forgeloop/secrets/credentials.json
   ```

   Also replace the GitHub placeholders with the real values for the existing
   app: `FORGELOOP_GITHUB_CLIENT_ID`, `FORGELOOP_GITHUB_CLIENT_SECRET`,
   `FORGELOOP_GITHUB_APP_SLUG`, `FORGELOOP_GITHUB_APP_ID`,
   `FORGELOOP_GITHUB_PRIVATE_KEY`, `FORGELOOP_GITHUB_WEBHOOK_SECRET`, and
   `FORGELOOP_GITHUB_INSTALLATION_STATE_SECRET`. Preserve the established
   organization and intake defaults unless you intentionally want to change
   policy. Keep `FORGELOOP_SECURITY_MODE=github`; do not enable the separate
   `production` Spring profile with this Compose setup.

   Remove runner-only values such as `ANTHROPIC_API_KEY` from the server copy of
   `.env`; configure provider credentials only on the machine running the
   ForgeLoop runner. Also omit personal MCP access tokens that the server does
   not need.

   The GitHub OAuth callback is
   `https://forgeloop.hookerhillstudios.com/login/oauth2/code/github`. The GitHub
   App setup callback is
   `https://forgeloop.hookerhillstudios.com/api/github/app/callback`; its webhook
   URL is `https://forgeloop.hookerhillstudios.com/api/github/webhooks`. These
   URLs do not change during the server move. Never commit `.env` or print its
   resolved contents with `docker compose config` when sharing diagnostics.
   The Codefront Labs hostname is an alternate ingress. Add its OAuth callback
   URL to the GitHub App configuration before signing in through that hostname.

4. Install the tunnel credential with restrictive host permissions. Transfer it
   over SSH/SCP or retrieve it from your secret manager; do not put it in the Git
   checkout. For the SCP transfer above:

   ```bash
   sudo install -d -o root -g root -m 700 /etc/forgeloop/secrets
   # The pinned cloudflared image runs as UID/GID 65532 and needs read access.
   sudo install -o root -g 65532 -m 640 "$HOME/forgeloop-tunnel.json" \
     /etc/forgeloop/secrets/credentials.json
   ```

   Then change the tunnel credential path and database password for this server.
   Do **not** transfer `cert.pem`: it grants account-wide tunnel administration
   and is not needed by the runtime connector. Remove temporary transfer files
   after verifying the installed copies.

   If you are transferring over SCP, the files first land in the Linux login
   user's home directory as shown above. Never transfer secrets through chat,
   issue comments, or a repository commit.

5. Start and check the application services first, without enabling the tunnel:

   ```bash
   cd /opt/forgeloop
   sudo docker compose up -d --build --wait
   sudo docker compose ps
   curl -fsS "http://$(docker compose port control-plane 8090)/actuator/health/readiness"
   curl -fsSI "http://$(docker compose port web 80)/"
   ```

   Confirm PostgreSQL, `control-plane`, `web`, `webhook-edge`, and
   `database-backup` are running/healthy before connecting the public tunnel.
   Investigate unhealthy services with `sudo docker compose logs --tail=200
   <service>`.

6. Start the Cloudflare connector after local checks pass:

   ```bash
   sudo docker compose --profile tunnel up -d --wait cloudflared
   sudo docker compose --profile tunnel ps
   sudo docker compose --profile tunnel logs --tail=100 cloudflared
   ```

   For a first install with no other active connector for this hostname, you may
   start everything in one command instead:

   ```bash
   sudo docker compose --profile tunnel up -d --build --wait
   ```

## Cut over from the workstation

The database and artifact volumes are host-local. Copying `.env` and the tunnel
credential does **not** move application data. If the server must retain existing
runs, repository connections, or evidence, take and verify database and artifact
backups and restore them on the server first; follow
[reliability and recovery](reliability.md). Do not point two independent
databases at the same public hostname.

### Existing Compose database password

The former Compose file initialized PostgreSQL with a credential checked into
source control. The updated stack requires a unique password and applies it to
PostgreSQL, the control plane, and scheduled backups. If retaining a database
volume created by that older configuration, changing `.env` alone is not enough:
PostgreSQL only applies `POSTGRES_PASSWORD` when initializing an empty volume.
Set the new unique `FORGELOOP_DATABASE_PASSWORD` in `.env`, then rotate the
database role password interactively while the existing container is running:

```bash
docker compose exec -it postgres psql -U forgeloop -d postgres
```

At the `psql` prompt, run `\password forgeloop`, enter the new random value
twice, then `\q`. Use the same value for `FORGELOOP_DATABASE_PASSWORD` in the
protected `.env`, then recreate services. Keep credentials out of shell history
and command-line arguments; if local PostgreSQL authentication prompts for a
password, enter the current value interactively.

To avoid Cloudflare distributing requests between two separate databases:

1. Prepare the server and start its non-tunnel services using steps 1-5 above.
2. During a short cutover window, stop the workstation's tunnel connector. For a
   Compose connector, run `docker compose --profile tunnel stop cloudflared` in
   the workstation checkout. If a separate Windows `Cloudflared` service is
   installed, stop and disable it as well; there must not be a second connector
   serving a different stack.
3. Start `cloudflared` on the server using step 6 and verify the public routes.
4. Keep the old workstation data and backups intact until the server deployment
   and restored data have been validated. Remove its tunnel credential only
   after cutover is accepted.

The tunnel UUID, DNS route, and GitHub callback/webhook URLs remain unchanged.
Do not run two connectors against different app/database state as a high
availability strategy; that requires shared state and a deliberately designed
multi-host deployment.

## Verify public access and ingress

From the server:

```bash
curl -fsSI https://forgeloop.hookerhillstudios.com/
curl -sSI https://forgeloop.hookerhillstudios.com/oauth2/authorization/github
curl -i -X POST https://forgeloop.hookerhillstudios.com/api/github/webhooks \
  -H 'Content-Type: application/json' -d '{}'
sudo ss -lnt
```

Expect the home page to return `200`, GitHub OAuth to redirect (`302`/`303`),
and the unsigned webhook to be rejected (`400`/`401`). In the listening-port
list, Compose ports 5432, 8090, 5173, and 8091 should be bound to `127.0.0.1`,
never `0.0.0.0` or a public interface. Also check the Cloudflare dashboard or
`docker compose --profile tunnel logs cloudflared` to confirm the connector is
healthy.

## Updates, restart, and backups

Docker's `unless-stopped` policy restarts the stack after a host reboot; Docker
itself must be enabled at boot. To deploy a reviewed update:

```bash
cd /opt/forgeloop
git pull --ff-only
sudo docker compose --profile tunnel up -d --build --wait
sudo docker compose --profile tunnel ps
```

To stop the stack without deleting its database or artifact data:

```bash
sudo docker compose --profile tunnel down
```

Never add `--volumes`/`-v` to `down` on a server with data you need. Compose's
`database-backup` service creates a database dump and artifact archive every six
hours in a local Docker volume. Confirm it is healthy and copy completed backup
bundles to encrypted off-host storage with an access-controlled retention
policy. A backup stored only on this server does not protect against server or
disk loss. See [reliability and recovery](reliability.md) for restore guidance.

## Workstation tunnel helper

The account-scoped `cert.pem` is only for tunnel administration. The runtime
uses the tunnel-specific JSON credential. From PowerShell, the checked-in helper
starts only the tunnel service after validating the credential path:

```powershell
.\scripts\Start-ForgeLoopTunnel.ps1
```

Use one connector configuration per active deployment. A stale Windows service
and a Compose connector can send traffic to different origins and cause
intermittent `503`s. Stop/disable the old service from an elevated PowerShell if
the Compose connector is the one in use:

```powershell
Stop-Service Cloudflared
Set-Service Cloudflared -StartupType Disabled
```

The GitHub App helper preserves the webhook secret and does not print it:

```powershell
node .\scripts\Update-GithubAppWebhook.mjs `
  https://forgeloop.hookerhillstudios.com/api/github/webhooks
node .\scripts\Update-GithubAppWebhook.mjs --deliveries
```

GitHub does not automatically retry failed webhook deliveries. Inspect delivery
status and redeliver a specific failed delivery with
`node scripts/Update-GithubAppWebhook.mjs --redeliver <delivery-id>` after the
service is healthy.
