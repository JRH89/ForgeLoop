# Deployment prerequisites

## GitHub App production activation

The GitHub App install entry point is implemented. Before enabling live repository synchronization, webhook intake, and pull-request delivery in a deployed environment, configure these externally managed values:

- `FORGELOOP_GITHUB_APP_SLUG`
- `FORGELOOP_GITHUB_APP_ID` and a secret-manager-injected PKCS#8 PEM private key in `FORGELOOP_GITHUB_PRIVATE_KEY` for short-lived installation-token exchange
- `FORGELOOP_GITHUB_WEBHOOK_SECRET`
- A public HTTPS callback and webhook URL

These values are deployment secrets and must be supplied through the deployment secret manager, never committed to this repository.

Configure the GitHub App's setup URL as `https://<control-plane>/api/github/app/callback`. ForgeLoop signs the installation state for ten minutes and records the resulting installation only after the callback validates that state. Configure the webhook URL as `https://<control-plane>/api/github/webhooks` and subscribe to `issues`, `pull_request`, `check_run`, and `check_suite` events. Pull request `closed` events with `merged: true` synchronize human merges into the run state. ForgeLoop also accepts GitHub's installation lifecycle deliveries to synchronize repository selection changes. Auto-merge has a scheduled reconciliation sweep as a fallback, but check events provide immediate response.

Use least-privilege repository permissions: **Contents — read/write**, **Checks — read/write**, **Issues — read-only**, **Pull requests — read/write**, and **Commit statuses — read-only**. GitHub adds **Metadata — read-only** automatically. Auto-merge cannot operate without pull-request write access and cannot establish the full success boundary without check and commit-status read access.

The self-hosted runner requests a lease-bound installation token, pushes one exact integrated commit with `--force-with-lease`, and reports the remote SHA. The control plane verifies that SHA through GitHub before accepting integration; it never receives repository file contents. After independent review, required verification, and human approval, the delivery adapter creates a successful ForgeLoop check run and opens one pull request per run. By default the PR is a draft and a human retains the merge boundary. An administrator may opt the organization into auto-merge; ForgeLoop then opens a ready PR, requires every GitHub check and commit status to finish successfully, verifies that the PR head still equals the reviewed SHA, and requests a squash merge using GitHub's expected-head guard. The immutable publication record and audit ledger store the resulting merge SHA. A scheduled retry sweep handles transient GitHub or webhook failures. Use a disposable installed repository to validate permissions before enabling the production policy.

The local named-tunnel setup, restricted public routes, GitHub App URLs, and production-host cutover are documented in [cloudflare-tunnel.md](cloudflare-tunnel.md).
