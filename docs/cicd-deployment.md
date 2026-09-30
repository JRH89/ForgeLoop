# Gitea webhook deployment

The host already runs the shared `webhook-multi-repo` service from
[`JRH89/cicd`](https://github.com/JRH89/cicd). This repository's `deploy.sh`
was installed from that project's Docker template and customized for ForgeLoop.
The shared service looks for `/home/jrh89/Work/forgeloop/deploy.sh` after a push
to `master`.

In the ForgeLoop Gitea repository, add a push-event webhook pointing to
`http://192.168.254.54:9001/deploy`. Use the host's LAN address, not
`localhost`, because Gitea runs in a container. Keep port 9001 reachable only
on the trusted network; the shared webhook service does not authenticate
requests. Do not reinstall or restart that service just for ForgeLoop.

On the current host, UFW allows TCP 9001 from the home LAN
`192.168.254.0/24` on `wlan0` and from Gitea's Docker network
`172.21.0.0/16` on bridge `br-faaf32d9a68c`. There is no public IPv4 or IPv6
allow rule for this port. If the LAN subnet or Docker network changes, update
the scoped firewall rule and test Gitea connectivity before relying on pushes.

The script refuses a dirty checkout or a non-`master` branch, then pulls
`origin/master` with fast-forward only. It builds and recreates only
`control-plane` and `web`, waits for readiness, and checks their loopback HTTP
endpoints. It does not run `docker compose down`, remove volumes, rebuild the
database, restart the tunnel, or prune host images. The shared service's
`FORCE_CLEAN_BUILD=true` setting only disables the cache for those two images.

Keep the repository-local Gitea credential file in `.git/` at mode `600` and
out of commits. The URL for `origin` contains the username but not the
password. A webhook deployment needs non-interactive Git access; verify it with
`GIT_TERMINAL_PROMPT=0 git ls-remote --exit-code origin refs/heads/master`
before enabling the hook. Push the same intended commits to both `origin` and
`github` when publishing changes, as required by `AGENTS.md`.

This workspace must be clean before a webhook can deploy it. Commit or
otherwise resolve local changes deliberately; do not discard them just to make
the hook pass. A successful local readiness check does not prove the public
Cloudflare route or all user flows are healthy.
